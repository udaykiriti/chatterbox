package javax.microedition.rms;

import java.io.IOException;

public final class RecordStore {
    public static RecordStore open(String name, boolean create) throws RecordStoreException {
        throw new RecordStoreException("stub");
    }
    public static String[] listStores() throws RecordStoreException {
        return new String[0];
    }
    public void close() throws RecordStoreException {}
    public void deleteRecord(long id) throws RecordStoreException {}
    public byte[] getRecord(long id) throws RecordStoreException, IOException { return null; }
    public void addRecord(long id, byte[] data) throws RecordStoreException, IOException {}
    public void setRecord(long id, byte[] data) throws RecordStoreException, IOException {}
    public void deleteAllRecords() throws RecordStoreException {}
    public String[] listRecordIds() throws RecordStoreException { return new String[0]; }
    public boolean recordExists(long id) throws RecordStoreException { return false; }
    public String getName() throws RecordStoreException { return ""; }
}
