package javax.microedition.io;

public interface SecurityConnection extends Connection {
    String getSecurityProtocol();
    String getCipherSuite();
}
