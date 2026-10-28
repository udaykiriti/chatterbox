package com.chatterbox.net;

import com.chatterbox.core.ServerState;

/**
 * Everything the connection needs to (re)establish itself, plus the knobs the
 * user can tune in the settings form.
 *
 * Assumption: TLS is attempted first when {@link #preferTls} is set, and we
 * silently fall back to plain socket:// if the Connector throws. Some S60
 * firmware refuses ssl:// without an installed root CA, and there is no way to
 * detect that other than by trying.
 *
 * SECURITY: When {@link #requireTls} is true, the connection will ONLY use TLS
 * and will fail rather than falling back to plaintext or gateway. This prevents
 * credential leakage on untrusted networks.
 */
public final class ConnectionConfig {

    /** Flood control: one line per delay, with a burst allowance. */
    public long floodDelayMs = 700;
    public int floodBurst = 4;

    /** Backoff for reconnect, doubling from min to max. */
    public long reconnectMinMs = 2000;
    public long reconnectMaxMs = 60000;

    /** Send PING when the connection has been idle this long. */
    public long keepaliveMs = 90000;

    /** Give up after this many consecutive failures; 0 = never give up. */
    public int maxRetries = 0;

    /** Lines to buffer while the socket is down before we start dropping. */
    public int maxQueue = 200;

    public boolean preferTls = true;

    /** If true, ONLY use TLS and fail instead of falling back to plaintext/gateway. */
    public boolean requireTls = false;

    /** Gateway fallback: host:port of our python relay, plain TCP. */
    public String gatewayHost = "";
    public int gatewayPort = 0;
    public String gatewayPassword = "";

    /** User-agent / real name sent in USER. */
    public String versionString = "Chatterbox 1.0";

    public ConnectionConfig copy() {
        ConnectionConfig c = new ConnectionConfig();
        c.floodDelayMs = floodDelayMs;
        c.floodBurst = floodBurst;
        c.reconnectMinMs = reconnectMinMs;
        c.reconnectMaxMs = reconnectMaxMs;
        c.keepaliveMs = keepaliveMs;
        c.maxRetries = maxRetries;
        c.maxQueue = maxQueue;
        c.preferTls = preferTls;
        c.requireTls = requireTls;
        c.gatewayHost = gatewayHost;
        c.gatewayPort = gatewayPort;
        c.gatewayPassword = gatewayPassword;
        c.versionString = versionString;
        return c;
    }

    public static ConnectionConfig fromServer(ServerState s) {
        ConnectionConfig c = new ConnectionConfig();
        c.preferTls = s.isSecure();
        c.requireTls = s.isSecure();
        return c;
    }

    public String describe() {
        return "flood " + floodDelayMs + "ms/" + floodBurst
                + ", backoff " + (reconnectMinMs / 1000) + "s-"
                + (reconnectMaxMs / 1000) + "s";
    }
}
