package com.chatterbox.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.SecureConnection;
import javax.microedition.io.SocketConnection;

import com.chatterbox.core.ServerState;

/**
 * Opens javax.microedition.io connections, with the TLS-then-plain fallback the
 * spec calls for.
 *
 * MIDP 2.0 notes that matter on real Nokia firmware:
 *  - SocketConnection is NOT in CLDC; it is the MIDP addition. javax.microedition
 *    .io.SocketConnection exists since MIDP 1.0 but is marked optional; on some
 *    very old Series 40 devices Connector.open("socket://") throws
 *    SecurityException. We surface that as a normal failure and fall through to
 *    the gateway.
 *  - There is NO connect timeout and NO read timeout on Connector.open for
 *    sockets. A black-holed TCP connect blocks the calling thread until the
 *    handset's own TCP timeout (often 2-3 minutes). That is why IrcConnection
 *    runs the connect attempt on its own thread and never on the MIDlet thread.
 *  - The spec SocketConnection has setSocketOption() (MIDP 2.0) with TCP
 *    options only; there is NO read timeout. We set KEEPALIVE when the
 *    caller wants a timeout so dead peers surface via the OS instead of
 *    blocking a read forever.
 *  - "ssl://" needs the hostname to match the certificate. On S60 3rd edition
 *    (E63/E71/C3) the TLS root store is small and often lacks the modern CA
 *    bundle, so failures are common and expected; we always fall back.
 *  - SECURITY: Fallback to plaintext or gateway sends passwords in cleartext.
 *    Use the gateway only on trusted networks. Consider enabling "Require TLS"
 *    in settings to disable fallback.
 */
public final class SocketFactory {

    private SocketFactory() {
    }

    /** Result of a connect attempt: the connection plus how we got it. */
    public static final class Opened {
        public InputStream in;
        public OutputStream out;
        public SocketConnection socket;
        public boolean secure;
        public String viaHost;
        public int viaPort;
        public boolean fallbackUsed;  // True if we fell back from TLS to plaintext/gateway

        public void close() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Nothing useful to do; the radio is already down.
                }
            }
        }
    }

    /**
     * Connect to a server. Order of attempts:
     *   1. ssl://host:port        (when preferTls)
     *   2. socket://host:port     (unless requireTls)
     *   3. socket://gateway:port  (when a gateway is configured, unless requireTls)
     *
     * @param readTimeoutMs socket read timeout, 0 for platform default.
     * @param requireTls if true, only attempt TLS and fail if it doesn't work.
     * @throws IOException when every attempt failed. The message is safe to
     *         show to the user.
     */
    public static Opened open(ServerState server, String gatewayHost, int gatewayPort,
                               boolean preferTls, boolean requireTls, int readTimeoutMs) throws IOException {

        StringBuffer errs = new StringBuffer(120);
        boolean triedTls = false;

        if (preferTls) {
            triedTls = true;
            try {
                Opened o = openOne("ssl://" + server.getHost() + ":" + server.getPort(),
                                   readTimeoutMs, true);
                o.viaHost = server.getHost();
                o.viaPort = server.getPort();
                o.fallbackUsed = false;
                return o;
            } catch (IOException e) {
                append(errs, "tls", e);
            } catch (SecurityException e) {
                append(errs, "tls", e);
            } catch (IllegalArgumentException e) {
                append(errs, "tls", e);
            }
        }

        if (requireTls) {
            throw new IOException("TLS required but failed: " + errs.toString());
        }

        try {
            Opened o = openOne("socket://" + server.getHost() + ":" + server.getPort(),
                               readTimeoutMs, false);
            o.viaHost = server.getHost();
            o.viaPort = server.getPort();
            o.fallbackUsed = triedTls;
            return o;
        } catch (IOException e) {
            append(errs, "direct", e);
        } catch (SecurityException e) {
            append(errs, "direct", e);
        } catch (IllegalArgumentException e) {
            append(errs, "direct", e);
        }

        if (gatewayHost != null && gatewayHost.length() > 0 && gatewayPort > 0) {
            try {
                Opened o = openOne("socket://" + gatewayHost + ":" + gatewayPort,
                                   readTimeoutMs, false);
                o.viaHost = gatewayHost;
                o.viaPort = gatewayPort;
                o.fallbackUsed = triedTls;
                return o;
            } catch (IOException e) {
                append(errs, "gateway", e);
            } catch (SecurityException e) {
                append(errs, "gateway", e);
            } catch (IllegalArgumentException e) {
                append(errs, "gateway", e);
            }
        }

        throw new IOException("Cannot reach " + server.getHost() + " (" + errs.toString() + ")");
    }

    private static void append(StringBuffer sb, String what, Exception e) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(what);
        sb.append(": ");
        String m = e.getMessage();
        if (m == null) {
            m = e.getClass().getName();
        }
        // Exception messages from Connector can be long; clip for the UI.
        sb.append(m.length() > 60 ? m.substring(0, 60) : m);
    }

    private static Opened openOne(String url, int readTimeoutMs, boolean secure)
            throws IOException {
        javax.microedition.io.Connection c = Connector.open(url);
        if (!(c instanceof SocketConnection)) {
            try {
                c.close();
            } catch (IOException ignored) {
                // fall through to the error below
            }
            throw new IOException("Not a socket: " + url);
        }
        SocketConnection sc = (SocketConnection) c;
        Opened o = new Opened();
        o.socket = sc;
        o.secure = secure && (c instanceof SecureConnection);
        try {
            if (readTimeoutMs > 0) {
                // Spec has no read timeout; TCP keepalive is the closest
                // supported equivalent for spotting dead peers.
                sc.setSocketOption(SocketConnection.KEEPALIVE, 1);
            }
            o.in = sc.openInputStream();
            o.out = sc.openOutputStream();
        } catch (IOException e) {
            try {
                sc.close();
            } catch (IOException ignored) {
                // best effort
            }
            throw e;
        }
        return o;
    }

    /** True if this connection negotiated TLS. */
    public static boolean isSecure(Opened o) {
        return o != null && o.secure;
    }
}
