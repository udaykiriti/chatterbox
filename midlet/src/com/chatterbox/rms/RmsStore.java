package com.chatterbox.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Thin, crash-tolerant wrapper over the MIDP 2.0 RecordStore API.
 *
 * Real RMS assigns record ids sequentially, so a caller cannot place a
 * record under an arbitrary key (this store's callers use stable logical
 * ids: 0..2 for magic/prefs/index and 1000 + slot for profiles). The
 * logical-id API is preserved by prefixing every stored record with its
 * logical id:
 *
 *   record bytes: [int logicalId][payload...]
 *
 * Reads enumerate the store and match on the prefix. Stores here are tiny
 * (a handful of records), so the linear scan is cheap. Record ids are never
 * reused after deleteRecord and payload rewrites happen in place, so a
 * stable record never moves.
 *
 * Notes that matter on real devices:
 *  - RecordStore.listRecordStores() can return null or throw if storage was
 *    removed mid-session. Everything here is wrapped so a corrupt store
 *    degrades to "no settings" rather than crashing the MIDlet on startApp.
 *  - We never trust a stored length. Every read validates that the byte
 *    count matches what the decoder is about to consume, because a
 *    truncated write (battery pull mid-save) is common on these devices and
 *    DataInputStream will happily throw EOFException deep inside a decoder.
 *  - Each call opens and closes the store; long-lived open handles were the
 *    main source of startup crashes on old firmware.
 */
public final class RmsStore {

    public static final int MAX_RECORD_BYTES = 24000;

    private final String name;

    public RmsStore(String name) {
        this.name = name == null ? "chatterbox" : name;
    }

    public String getName() {
        return name;
    }

    public boolean exists() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            return true;
        } catch (RecordStoreException e) {
            return false;
        } finally {
            closeQuietly(rs);
        }
    }

    public void delete() {
        try {
            RecordStore.deleteRecordStore(name);
        } catch (RecordStoreException e) {
            // Nothing to delete is a success for our purposes.
        }
    }

    public String[] list() {
        try {
            String[] out = RecordStore.listRecordStores();
            return out == null ? new String[0] : out;
        } catch (Throwable t) {
            return new String[0];
        }
    }

    /** @return the payload, or null when absent or unreadable. */
    public byte[] read(long id) {
        if (!fitsInt(id)) {
            return null;
        }
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    int recId = en.nextRecordId();
                    byte[] rec = rs.getRecord(recId);
                    if (rec != null && rec.length >= 4 && readInt(rec, 0) == (int) id) {
                        byte[] out = new byte[rec.length - 4];
                        System.arraycopy(rec, 4, out, 0, out.length);
                        return out;
                    }
                }
            } finally {
                en.destroy();
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(rs);
        }
    }

    /** @return true on success. A failed write leaves the old record intact. */
    public boolean write(long id, byte[] data) {
        if (data == null || data.length > MAX_RECORD_BYTES || !fitsInt(id)) {
            return false;
        }
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, true);
            byte[] framed = withPrefix((int) id, data);
            // Rewrite the record in place when one with this logical id exists.
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    int recId = en.nextRecordId();
                    byte[] rec = rs.getRecord(recId);
                    if (rec != null && rec.length >= 4 && readInt(rec, 0) == (int) id) {
                        rs.setRecord(recId, framed, 0, framed.length);
                        return true;
                    }
                }
            } finally {
                en.destroy();
            }
            rs.addRecord(framed, 0, framed.length);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(rs);
        }
    }

    public boolean remove(long id) {
        if (!fitsInt(id)) {
            return false;
        }
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    int recId = en.nextRecordId();
                    byte[] rec = rs.getRecord(recId);
                    if (rec != null && rec.length >= 4 && readInt(rec, 0) == (int) id) {
                        rs.deleteRecord(recId);
                        return true;
                    }
                }
            } finally {
                en.destroy();
            }
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(rs);
        }
    }

    /** Ids sorted ascending, so profile order is stable across restarts. */
    public long[] ids() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            long[] out = new long[rs.getNumRecords()];
            int n = 0;
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            try {
                while (en.hasNextElement()) {
                    int recId = en.nextRecordId();
                    byte[] rec = rs.getRecord(recId);
                    if (rec != null && rec.length >= 4) {
                        long logical = readInt(rec, 0) & 0xFFFFFFFFL;
                        boolean seen = false;
                        for (int i = 0; i < n && !seen; i++) {
                            seen = out[i] == logical;
                        }
                        if (!seen) {
                            out[n++] = logical;
                        }
                    }
                }
            } finally {
                en.destroy();
            }
            long[] exact = new long[n];
            System.arraycopy(out, 0, exact, 0, n);
            bubbleSort(exact);
            return exact;
        } catch (Throwable t) {
            return new long[0];
        } finally {
            closeQuietly(rs);
        }
    }

    // ---- logical-id framing ----

    private static boolean fitsInt(long v) {
        return v >= 0 && v <= 0x7FFFFFFFL && v == (long) (int) v;
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static byte[] withPrefix(int id, byte[] data) {
        byte[] framed = new byte[data.length + 4];
        framed[0] = (byte) (id >>> 24);
        framed[1] = (byte) (id >>> 16);
        framed[2] = (byte) (id >>> 8);
        framed[3] = (byte) id;
        System.arraycopy(data, 0, framed, 4, data.length);
        return framed;
    }

    private static void bubbleSort(long[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            for (int j = 0; j < a.length - 1 - i; j++) {
                if (a[j] > a[j + 1]) {
                    long t = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = t;
                }
            }
        }
    }

    private static void closeQuietly(RecordStore rs) {
        if (rs == null) {
            return;
        }
        try {
            rs.closeRecordStore();
        } catch (Throwable t) {
            // Ignored on purpose.
        }
    }

    // ---- stream helpers with explicit bounds checking ----

    public static DataOutputStream out(ByteArrayOutputStream bos) {
        return new DataOutputStream(bos);
    }

    /**
     * Decode a record with a hard 24 KB ceiling and a strict length check.
     * @throws IOException when the payload is truncated.
     */
    public static DataInputStream in(byte[] data) throws IOException {
        if (data == null) {
            throw new IOException("null record");
        }
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    /** Read a UTF string with a length prefix, defaulting to "" on damage. */
    public static String readString(DataInputStream in) {
        try {
            int n = in.readInt();
            if (n < 0 || n > MAX_RECORD_BYTES) {
                throw new IOException("bad string length " + n);
            }
            if (n == 0) {
                return "";
            }
            byte[] b = new byte[n];
            in.readFully(b);
            return com.chatterbox.proto.Utf8.decode(b);
        } catch (IOException e) {
            return "";
        }
    }

    public static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = com.chatterbox.proto.Utf8.encode(s == null ? "" : s);
        out.writeInt(b.length);
        out.write(b);
    }
}
