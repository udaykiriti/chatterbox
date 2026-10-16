package javax.microedition.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Compile-time stub of the MIDP 2.0 SocketConnection API (JSR 118).
 * Spec option constants match the platform exactly.
 */
public interface SocketConnection extends Connection {
    byte DELAY = 0;
    byte LINGER = 1;
    byte KEEPALIVE = 2;
    byte RCVBUF = 3;
    byte SNDBUF = 4;

    void setSocketOption(byte option, int value)
            throws IllegalArgumentException, IOException;
    int getSocketOption(byte option)
            throws IllegalArgumentException, IOException;
    String getLocalAddress() throws IOException;
    int getLocalPort() throws IOException;
    String getAddress() throws IOException;
    int getPort() throws IOException;
    InputStream openInputStream() throws IOException;
    OutputStream openOutputStream() throws IOException;
}
