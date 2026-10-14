package javax.microedition.io;

import java.io.IOException;

public interface Connection {
    void close() throws IOException;
    String getURL();
    String getScheme();
    String getHost();
    int getPort();
}
