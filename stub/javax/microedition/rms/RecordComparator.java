package javax.microedition.rms;

/** Stub of the MIDP 2.0 RecordComparator interface (JSR 118). */
public interface RecordComparator {
    int EQUIVALENT = 0;
    int PRECEDES = 1;
    int FOLLOWS = 2;

    int compare(byte[] rec1, byte[] rec2);
}
