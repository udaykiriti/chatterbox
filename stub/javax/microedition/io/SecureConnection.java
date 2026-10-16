package javax.microedition.io;

import java.io.IOException;

/**
 * Compile-time stub of the MIDP 2.0 SecureConnection API (JSR 118),
 * returned by Connector.open("ssl://..."). See stub/README.
 */
public interface SecureConnection extends Connection {
    SecurityInfo getSecurityInfo() throws IOException;
}
