package javax.microedition.rms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Compile-time stub of the MIDP 2.0 RecordStore API (JSR 118) with a working
 * in-memory implementation, so desktop builds and unit tests exercise the
 * exact spec signatures. Device/emulator builds use the real platform
 * classes; nothing here is packaged.
 */
public final class RecordStore {

    /** Per-named-store data shared by all open handles in this process. */
    private static final class Db {
        final Vector ids = new Vector();     // Integer record ids (ascending)
        final Vector records = new Vector(); // byte[] payloads, parallel to ids
        int nextId = 1;
    }

    private static final Hashtable DBS = new Hashtable(); // String name -> Db

    private final Db db;
    private final String name;
    private boolean open = true;

    private RecordStore(Db db, String name) {
        this.db = db;
        this.name = name;
    }

    // ---- static API ----

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary)
            throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException {
        if (recordStoreName == null) {
            throw new RecordStoreException("null store name");
        }
        Db db = (Db) DBS.get(recordStoreName);
        if (db == null) {
            if (!createIfNecessary) {
                throw new RecordStoreNotFoundException(recordStoreName);
            }
            db = new Db();
            DBS.put(recordStoreName, db);
        }
        return new RecordStore(db, recordStoreName);
    }

    public static void deleteRecordStore(String recordStoreName)
            throws RecordStoreException, RecordStoreNotFoundException {
        if (DBS.get(recordStoreName) == null) {
            throw new RecordStoreNotFoundException(recordStoreName);
        }
        DBS.remove(recordStoreName);
    }

    public static String[] listRecordStores() {
        int n = DBS.size();
        String[] out = new String[n];
        Enumeration en = DBS.keys();
        int i = 0;
        while (en.hasMoreElements()) {
            out[i++] = (String) en.nextElement();
        }
        return out;
    }

    // ---- instance API ----

    private void checkOpen() throws RecordStoreNotOpenException {
        if (!open) {
            throw new RecordStoreNotOpenException(name);
        }
    }

    private int slotFor(int recordId) throws InvalidRecordIDException {
        Integer key = new Integer(recordId);
        for (int i = 0; i < db.ids.size(); i++) {
            if (db.ids.elementAt(i).equals(key)) {
                return i;
            }
        }
        throw new InvalidRecordIDException(recordId);
    }

    public void closeRecordStore()
            throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        open = false;
    }

    public String getName() throws RecordStoreNotOpenException {
        checkOpen();
        return name;
    }

    public int getNumRecords() throws RecordStoreNotOpenException {
        checkOpen();
        return db.records.size();
    }

    public int getNextRecordID()
            throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        return db.nextId;
    }

    public int getRecordSize(int recordId)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        byte[] data = (byte[]) db.records.elementAt(slotFor(recordId));
        return data.length;
    }

    public int getSize() throws RecordStoreNotOpenException {
        checkOpen();
        int total = 0;
        for (int i = 0; i < db.records.size(); i++) {
            total += ((byte[]) db.records.elementAt(i)).length;
        }
        return total;
    }

    public byte[] getRecord(int recordId)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        return (byte[]) db.records.elementAt(slotFor(recordId));
    }

    public void setRecord(int recordId, byte[] newData, int offset, int numBytes)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException, RecordStoreFullException {
        checkOpen();
        if (newData == null || numBytes < 0 || offset < 0 || offset + numBytes > newData.length) {
            throw new RecordStoreException("bad setRecord range");
        }
        byte[] copy = new byte[numBytes];
        System.arraycopy(newData, offset, copy, 0, numBytes);
        db.records.setElementAt(copy, slotFor(recordId));
    }

    public void deleteRecord(int recordId)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        int slot = slotFor(recordId);
        db.ids.removeElementAt(slot);
        db.records.removeElementAt(slot);
    }

    public int addRecord(byte[] data, int offset, int numBytes)
            throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        checkOpen();
        if (data == null || numBytes < 0 || offset < 0 || offset + numBytes > data.length) {
            throw new RecordStoreException("bad addRecord range");
        }
        byte[] copy = new byte[numBytes];
        System.arraycopy(data, offset, copy, 0, numBytes);
        int id = db.nextId++;
        db.ids.addElement(new Integer(id));
        db.records.addElement(copy);
        return id;
    }

    public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator,
            boolean keepUpdated) throws RecordStoreNotOpenException {
        checkOpen();
        return new SnapshotEnumeration(filter, comparator);
    }

    // ---- enumeration over a stable snapshot ----

    private final class SnapshotEnumeration implements RecordEnumeration {
        private final int[] ids;
        private final Vector payloads; // byte[], parallel to ids
        private int index = 0;

        SnapshotEnumeration(RecordFilter filter, RecordComparator comparator) {
            Vector idList = new Vector();
            Vector payList = new Vector();
            for (int i = 0; i < db.ids.size(); i++) {
                int id = ((Integer) db.ids.elementAt(i)).intValue();
                byte[] payload = (byte[]) db.records.elementAt(i);
                if (filter != null && !filter.matches(payload)) {
                    continue;
                }
                if (comparator == null) {
                    idList.addElement(new Integer(id));
                    payList.addElement(payload);
                } else {
                    // Insertion sort by comparator.
                    int pos = 0;
                    while (pos < idList.size() && comparator.compare(
                            (byte[]) payList.elementAt(pos), payload) == RecordComparator.PRECEDES) {
                        pos++;
                    }
                    idList.insertElementAt(new Integer(id), pos);
                    payList.insertElementAt(payload, pos);
                }
            }
            ids = new int[idList.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = ((Integer) idList.elementAt(i)).intValue();
            }
            payloads = payList;
        }

        public int numRecords() {
            return ids.length;
        }

        public boolean hasNextElement() {
            return index < ids.length;
        }

        public boolean hasPreviousElement() {
            return index > 0;
        }

        public int nextRecordId() throws InvalidRecordIDException {
            if (!hasNextElement()) {
                throw new InvalidRecordIDException();
            }
            return ids[index++];
        }

        public byte[] nextRecord()
                throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
            int id = nextRecordId();
            return payloadFor(id);
        }

        public int previousRecordId() throws InvalidRecordIDException {
            if (!hasPreviousElement()) {
                throw new InvalidRecordIDException();
            }
            return ids[--index];
        }

        public byte[] previousRecord()
                throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
            int id = previousRecordId();
            return payloadFor(id);
        }

        public void reset() {
            index = 0;
        }

        public void keepUpdated(boolean keepUpdated) { /* snapshot semantics */ }

        public void rebuild() { /* snapshot semantics */ }

        public void destroy() { /* snapshot semantics */ }

        private byte[] payloadFor(int recordId) throws InvalidRecordIDException {
            Integer key = new Integer(recordId);
            for (int i = 0; i < db.ids.size(); i++) {
                if (db.ids.elementAt(i).equals(key)) {
                    return (byte[]) db.records.elementAt(i);
                }
            }
            throw new InvalidRecordIDException(recordId);
        }
    }
}
