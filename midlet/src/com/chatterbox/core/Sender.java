package com.chatterbox.core;

/**
 * The outbound half of a session, implemented by the network layer so that
 * core does not depend on javax.microedition.io.
 */
public interface Sender {

    /** Queue a fully serialised line (with CRLF). Must never block. */
    void send(String line);

    boolean isConnected();

    /** Force-close the transport. Does not send QUIT. */
    void close();
}
