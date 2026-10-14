package javax.microedition.io;

import java.io.IOException;

public class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    public static javax.microedition.io.Connection open(String url) throws IOException {
        throw new IOException("stub");
    }

    public static javax.microedition.io.Connection open(String url, int mode, boolean timeout)
            throws IOException {
        throw new IOException("stub");
    }

    protected Connector() {}
}
