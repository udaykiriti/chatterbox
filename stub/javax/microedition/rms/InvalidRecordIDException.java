package javax.microedition.rms;

/** Stub of the MIDP 2.0 exception (JSR 118). */
public class InvalidRecordIDException extends RecordStoreException {
    public InvalidRecordIDException() {}
    public InvalidRecordIDException(int recordId) { super("record id " + recordId); }
    public InvalidRecordIDException(String message) { super(message); }
}
