package javax.microedition.io;

import java.io.IOException;

/**
 * Compile-time stub of the CLDC 1.1 Connection interface (JSR 30).
 * The platform defines only close(); the accessors here match the emulator's
 * GCF plumbing so desktop builds and tests compile unchanged.
 */
public interface Connection {
    void close() throws IOException;
}
