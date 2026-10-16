package javax.microedition.rms;

/** Stub of the MIDP 2.0 RecordEnumeration interface (JSR 118). */
public interface RecordEnumeration {
    int numRecords();
    boolean hasNextElement();
    boolean hasPreviousElement();
    int nextRecordId() throws InvalidRecordIDException;
    byte[] nextRecord()
            throws InvalidRecordIDException, RecordStoreNotOpenException, RecordStoreException;
    int previousRecordId() throws InvalidRecordIDException;
    byte[] previousRecord()
            throws InvalidRecordIDException, RecordStoreNotOpenException, RecordStoreException;
    void reset();
    void keepUpdated(boolean keepUpdated);
    void rebuild();
    void destroy();
}
