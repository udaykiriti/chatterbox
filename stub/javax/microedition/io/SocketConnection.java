package javax.microedition.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SocketConnection extends Connection {
    String getLocalAddress();
    int getLocalPort();
    String getRemoteAddress();
    int getRemotePort();
    void setSocketTimeout(int timeout) throws IOException;
    InputStream getInputStream() throws IOException;
    OutputStream getOutputStream() throws IOException;
}
