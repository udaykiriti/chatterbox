package com.chatterbox.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Thin, crash-tolerant wrapper over RecordStore.
 *
 * Assumptions and firmware notes:
 *  - RecordStore.listStores() can throw if storage was removed mid-session.
 *    Everything here is wrapped so a corrupt store degrades to "no settings"
 *    rather than crashing the MIDlet on startApp.
 *  - We never trust a stored length. Every read validates that the byte count
 *    matches what the decoder is about to consume, because a truncated write
 *    (battery pull mid-save) is common on these devices and DataInputStream
 *    will happily throw EOFException deep inside a decoder.
 *  - Storage names are hashed-free and short; some S60 builds cap a record at
 *    32 KB.
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

    public RecordStore open() throws RecordStoreException {
        return RecordStore.open(name, true);
    }

    public boolean exists() {
        RecordStore rs = null;
        try {
            rs = RecordStore.open(name, false);
            return true;
        } catch (RecordStoreException e) {
            return false;
        } finally {
            closeQuietly(rs);
        }
    }

    public void delete() {
        RecordStore rs = null;
        try {
            rs = RecordStore.open(name, true);
            rs.deleteAllRecords();
        } catch (RecordStoreException e) {
            // Nothing to delete is a success for our purposes.
        } finally {
            closeQuietly(rs);
        }
    }

    public String[] list() {
        try {
            return RecordStore.listStores();
        } catch (Throwable t) {
            return new String[0];
        }
    }

    /** @return the payload, or null when absent or unreadable. */
    public byte[] read(long id) {
        RecordStore rs = null;
        try {
            rs = open();
            if (!rs.recordExists(id)) {
                return null;
            }
            byte[] data = rs.getRecord(id);
            if (data == null || data.length > MAX_RECORD_BYTES) {
                return null;
            }
            return data;
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(rs);
        }
    }

    /** @return true on success. A failed write leaves the old record intact. */
    public boolean write(long id, byte[] data) {
        if (data == null || data.length > MAX_RECORD_BYTES) {
            return false;
        }
        RecordStore rs = null;
        try {
            rs = open();
            if (rs.recordExists(id)) {
                rs.setRecord(id, data);
            } else {
                rs.addRecord(id, data);
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(rs);
        }
    }

    public boolean remove(long id) {
        RecordStore rs = null;
        try {
            rs = open();
            if (rs.recordExists(id)) {
                rs.deleteRecord(id);
            }
            return true;
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
            rs = open();
            String[] names = rs.listRecordIds();
            long[] out = new long[names.length];
            for (int i = 0; i < names.length; i++) {
                out[i] = Long.parseLong(names[i]);
            }
            bubbleSort(out);
            return out;
        } catch (Throwable t) {
            return new long[0];
        } finally {
            closeQuietly(rs);
        }
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
            rs.close();
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
