package com.chatterbox.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import com.chatterbox.core.IrcEventListener;
import com.chatterbox.core.Sender;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.Session;
import com.chatterbox.core.Str;
import com.chatterbox.proto.IrcCodec;
import com.chatterbox.proto.LineReader;

/**
 * One IRC connection, implemented as a self-restoring state machine.
 *
 * THREAD MODEL (per connection, three threads):
 *
 *   thread A "irc-conn-<id>"  the owner thread.
 *       Loop: connect -> register -> wait for death -> backoff -> repeat.
 *       It does NOT read the socket. It only supervises.
 *
 *   thread B "irc-rd-<id>"     reader. Blocks on InputStream.read(), frames
 *       lines through LineReader, and calls Session.feed() which raises
 *       IrcEventListener callbacks. All callbacks therefore arrive on thread B.
 *       UI code must marshal those callbacks onto the MIDlet thread.
 *
 *   thread C "irc-wr-<id>"     writer. Polls FloodQueue on a schedule derived
 *       from nextDueIn(), so it sleeps when idle and never spins. Also drives
 *       the 90 s keepalive PING.
 *
 * Only thread A touches Connector.open(), because it can block for minutes on a
 * black-holed route and the MIDlet thread must stay responsive for the UI.
 *
 * Assumptions:
 *  - readTimeoutMs is set to keepaliveMs + 20 s. If the server goes silent for
 *    longer than that we treat the socket as dead and reconnect, because MIDP
 *    gives us no way to distinguish "idle" from "half-open".
 *  - The gateway password, if configured, is sent as PASS on the first line of
 *    the connection, matching the gateway contract (see gateway/server.py).
 *  - Reconnect uses exponential backoff with jitter to avoid a thundering herd
 *    when a whole cell reconnects at once after a network outage.
 */
public final class IrcConnection implements Sender, Runnable {
    public Sender getSender() { return this; }

    private final ServerState server;
    private final Session session;
    private final ConnectionConfig config;
    private final com.chatterbox.rms.LogStore log;
    private IrcEventListener listener;

    private FloodQueue queue;
    private volatile boolean running;
    private volatile boolean stopping;
    private volatile boolean connected;

    private Thread ownerThread;
    private Thread readerThread;
    private Thread writerThread;

    private volatile SocketFactory.Opened transport;
    private volatile InputStream in;
    private volatile OutputStream out;
    private volatile long lastWriteAt;
    private volatile int consecutiveFailures;
    private volatile String lastError = "";

    /** Gateway handshake: true while we still owe the relay its password. */
    private boolean gatewayPending;

    public IrcConnection(ServerState server, ConnectionConfig config) {
        this.server = server;
        this.config = config;
        this.queue = new FloodQueue(config.maxQueue, config.floodDelayMs, config.floodBurst);
        this.session = new Session(server, this);
        this.session.setListener(new SessionEvents());
        this.log = new com.chatterbox.rms.LogStore(server.getId());
    }

    public Session getSession() {
        return session;
    }

    public ServerState getServer() {
        return server;
    }

    public ConnectionConfig getConfig() {
        return config;
    }

    /** Optional local RMS scrollback log for this server. */
    public com.chatterbox.rms.LogStore getLog() {
        return log;
    }

    public void setListener(IrcEventListener l) {
        this.listener = l;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return connected;
    }

    public String lastError() {
        return lastError;
    }

    public int queuedLines() {
        return queue.pending();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Start supervising. Returns immediately; the work happens on thread A. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        stopping = false;
        consecutiveFailures = 0;
        ownerThread = new Thread(this);
        ownerThread.setName("irc-conn-" + server.getId());
        ownerThread.start();
    }

    /**
     * Clean shutdown: send QUIT, give the writer a moment to flush, then close.
     * Safe to call from pauseApp and from the quit command.
     */
    public synchronized void stopGracefully(String reason) {
        if (!running && !connected) {
            return;
        }
        stopping = true;
        try {
            if (connected) {
                session.reset();
                send(IrcCodec.quit(reason == null ? "Chatterbox" : reason));
                // Let the writer thread drain the QUIT and any tail.
                long deadline = System.currentTimeMillis() + 1500;
                while (System.currentTimeMillis() < deadline && queue.pending() > 0) {
                    Thread.sleep(50);
                }
            }
        } catch (Throwable t) {
            // A midlet must never let an exception escape into the AMS here.
        }
        running = false;
        connected = false;
        closeTransport();
        Thread t = ownerThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                // nothing to do
            }
        }
    }

    /** Abort without QUIT. Used when the transport is already broken. */
    public synchronized void abort() {
        running = false;
        connected = false;
        closeTransport();
    }

    public void close() {
        abort();
    }

    private void closeTransport() {
        SocketFactory.Opened o = transport;
        transport = null;
        in = null;
        out = null;
        if (o != null) {
            o.close();
        }
    }

    // ------------------------------------------------------------------
    // Sender
    // ------------------------------------------------------------------

    public void send(String line) {
        if (line == null) {
            return;
        }
        if (!connected) {
            // Register-phase lines are worth keeping: we replay them on the
            // next successful connect so a message typed during a blip is not
            // lost. Once the queue is full, FloodQueue drops the oldest.
            queue.offer(line);
            return;
        }
        if (!queue.offer(line)) {
            session.post(null, server.getHost(), 10,
                    "Output queue full, some lines were dropped.");
        }
    }

    /** Convenience used by the UI command layer. */
    public void sendCommand(String line) {
        send(line);
    }

    // ------------------------------------------------------------------
    // Owner thread
    // ------------------------------------------------------------------

    public void run() {
        while (running && !stopping) {
            int readTimeout = (int) (config.keepaliveMs + 20000);
            try {
                server.setState(ServerState.CONNECTING);
                notifyState(ServerState.CONNECTING, "connecting to " + server.getHost());

                SocketFactory.Opened o = SocketFactory.open(
                        server, config.gatewayHost, config.gatewayPort,
                        config.preferTls, config.requireTls, readTimeout);
                transport = o;
                in = o.in;
                out = o.out;
                gatewayPending = isGateway(o);
                connected = true;
                consecutiveFailures = 0;
                lastError = "";

                readerThread = new Thread(new Reader(), "irc-rd-" + server.getId());
                readerThread.start();
                writerThread = new Thread(new Writer(), "irc-wr-" + server.getId());
                writerThread.start();

                session.reset();
                server.setState(ServerState.REGISTERING);
                notifyState(ServerState.REGISTERING, "registering");

                // The gateway wants its shared password on the first line, before
                // anything else, so it can decide whether to open the TLS leg.
                if (gatewayPending && config.gatewayPassword.length() > 0) {
                    writeRaw(config.gatewayPassword + "\r\n");
                }

                session.register();

                // Supervise: wait until the reader reports the socket is dead.
                while (running && !stopping && connected) {
                    Thread.sleep(500);
                }

                stopThreads();
                closeTransport();
                connected = false;

                if (stopping || !running) {
                    break;
                }

                server.setState(ServerState.RECONNECTING);
                notifyState(ServerState.RECONNECTING, "reconnecting");
                sleepBackoff();

            } catch (IOException ioe) {
                lastError = ioe.getMessage() == null ? "connection failed" : ioe.getMessage();
                connected = false;
                stopThreads();
                closeTransport();
                notifyError(lastError);
                server.setState(ServerState.RECONNECTING);
                notifyState(ServerState.RECONNECTING, lastError);
                if (!running || stopping) {
                    break;
                }
                consecutiveFailures++;
                if (config.maxRetries > 0 && consecutiveFailures > config.maxRetries) {
                    lastError = "giving up after " + consecutiveFailures + " attempts";
                    running = false;
                    notifyError(lastError);
                    break;
                }
                sleepBackoff();
            } catch (Throwable t) {
                // Defensive: an unexpected runtime failure must not kill the
                // thread silently and strand the UI in "connecting" forever.
                lastError = "internal error: " + t.getClass().getName();
                connected = false;
                stopThreads();
                closeTransport();
                notifyError(lastError);
                running = false;
                break;
            }
        }
        server.onDisconnect();
        notifyState(ServerState.DISCONNECTED, lastError);
    }

    private boolean isGateway(SocketFactory.Opened o) {
        return config.gatewayHost != null && config.gatewayHost.length() > 0
                && config.gatewayHost.equals(o.viaHost);
    }

    private void stopThreads() {
        Thread r = readerThread;
        Thread w = writerThread;
        readerThread = null;
        writerThread = null;
        // Closing the transport in closeTransport() unblocks the reader's read().
        closeTransport();
        if (r != null && r != Thread.currentThread()) {
            try {
                r.join(2000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (w != null && w != Thread.currentThread()) {
            try {
                w.join(2000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Exponential backoff: min * 2^(failures-1), capped, with +/-20% jitter.
     */
    /** Sleeps the backoff interval. Never throws; interruption ends the wait. */
    private void sleepBackoff() {
        int f = consecutiveFailures;
        if (f < 1) {
            f = 1;
        }
        long delay = config.reconnectMinMs;
        for (int i = 1; i < f && delay < config.reconnectMaxMs; i++) {
            delay <<= 1;
        }
        if (delay > config.reconnectMaxMs) {
            delay = config.reconnectMaxMs;
        }
        long jitter = (long) (delay * 0.2 * Math.random());
        long total = delay - delay / 10 + jitter;
        notifyState(ServerState.RECONNECTING,
                "retry in " + (total / 1000) + "s (attempt " + f + ")");
        try {
            Thread.sleep(total);
        } catch (InterruptedException e) {
            // Shutdown requested mid-wait: leave the interrupt flag set.
            running = false;
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Reader
    // ------------------------------------------------------------------

    private final class Reader implements Runnable {
        public void run() {
            LineReader framer = new LineReader();
            // 4 KB reads: NAMES/JOIN bursts arrive in tens of KB, and the old
            // 1 KB buffer paid a syscall per KB. Still tiny against the heap.
            byte[] buf = new byte[4096];
            String reason = null;
            try {
                while (running && !stopping && connected) {
                    InputStream is = in;
                    if (is == null) {
                        break;
                    }
                    int n = is.read(buf);
                    if (n < 0) {
                        reason = "server closed the connection";
                        break;
                    }
                    if (n == 0) {
                        continue;
                    }
                    Vector lines = framer.feed(buf, 0, n);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = (String) lines.elementAt(i);
                        try {
                            session.feedLine(line);
                        } catch (Throwable t) {
                            // One bad line must never kill the connection.
                            session.post(null, server.getHost(), 11,
                                    "error handling line: " + t.getClass().getName());
                        }
                    }
                }
            } catch (IOException e) {
                reason = "network error: " + Str.clip(e.getMessage(), 80);
            } catch (Throwable t) {
                reason = "read failure: " + t.getClass().getName();
            } finally {
                connected = false;
                if (reason != null && running && !stopping) {
                    lastError = reason;
                    session.post(null, server.getHost(), 10, "*** " + reason);
                    notifyError(reason);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Writer
    // ------------------------------------------------------------------

    private final class Writer implements Runnable {
        public void run() {
            try {
                long lastKeepalive = System.currentTimeMillis();
                while (running && !stopping && connected) {
                    long due = queue.nextDueIn();
                    if (due < 0) {
                        // Nothing queued. Sleep long when the connection has
                        // been quiet (overnight battery), but stay snappy
                        // right after activity so a typed line sends at once.
                        long quiet = System.currentTimeMillis() - lastWriteAt;
                        Thread.sleep(quiet > 30000 ? 1000 : 250);
                    } else if (due == 0) {
                        String line = queue.poll();
                        if (line != null) {
                            writeRaw(line);
                        } else {
                            Thread.sleep(20);
                        }
                    } else {
                        Thread.sleep(Math.min(due, 250L));
                    }

                    // One clock read per loop instead of three, and the ping
                    // itself throttled: the old code re-pinged every tick once
                    // idle (until the PONG arrived), spamming the server.
                    long now = System.currentTimeMillis();
                    if (connected && now - lastWriteAt > config.keepaliveMs
                            && now - lastKeepalive > 30000) {
                        lastKeepalive = now;
                        session.sendKeepalivePing();
                    }
                }
            } catch (InterruptedException e) {
                // shutdown
            } catch (Throwable t) {
                lastError = "write failure: " + t.getClass().getName();
                connected = false;
            }
        }
    }

    /** Blocking write of one line, CRLF already present. */
    private void writeRaw(String line) {
        OutputStream os = out;
        if (os == null || line == null) {
            return;
        }
        try {
            byte[] b = com.chatterbox.proto.Utf8.encode(line);
            os.write(b);
            os.flush();
            lastWriteAt = System.currentTimeMillis();
        } catch (IOException e) {
            lastError = "write failed: " + Str.clip(e.getMessage(), 60);
            connected = false;
            notifyError(lastError);
        } catch (Throwable t) {
            lastError = "write error: " + t.getClass().getName();
            connected = false;
            notifyError(lastError);
        }
    }

    // ------------------------------------------------------------------
    // Listener plumbing (bridges Session callbacks to the app listener)
    // ------------------------------------------------------------------

    private void notifyState(int state, String text) {
        server.setStatusText(text);
        if (listener != null) {
            listener.onStateChanged(server, state, text);
        }
    }

    private void notifyError(String reason) {
        if (listener != null) {
            listener.onError(server, reason);
        }
    }

    /**
     * Internal adapter. Session raises these on the reader thread; we simply
     * forward them. The UI layer is responsible for marshalling.
     */
    private final class SessionEvents implements IrcEventListener {
        public void onMessage(ServerState s, com.chatterbox.core.ChannelState w,
                              com.chatterbox.core.Message m) {
            if (listener != null) {
                listener.onMessage(s, w, m);
            }
        }

        public void onStateChanged(ServerState s, int state, String text) {
            notifyState(state, text);
        }

        public void onNickInUse(ServerState s, String attempted, String chosen) {
            if (listener != null) {
                listener.onNickInUse(s, attempted, chosen);
            }
        }

        public void onWindowOpened(ServerState s, com.chatterbox.core.ChannelState w) {
            if (listener != null) {
                listener.onWindowOpened(s, w);
            }
        }

        public void onWindowClosed(ServerState s, com.chatterbox.core.ChannelState w) {
            if (listener != null) {
                listener.onWindowClosed(s, w);
            }
        }

        public void onTopicChanged(ServerState s, com.chatterbox.core.ChannelState c,
                                   String topic) {
            if (listener != null) {
                listener.onTopicChanged(s, c, topic);
            }
        }

        public void onError(ServerState s, String reason) {
            notifyError(reason);
        }

        public void onAlert(ServerState s, com.chatterbox.core.ChannelState w,
                            boolean isMention, boolean isPrivate) {
            if (listener != null) {
                listener.onAlert(s, w, isMention, isPrivate);
            }
        }

        public void onLag(ServerState s, long millis) {
            if (listener != null) {
                listener.onLag(s, millis);
            }
        }
    }

    /** Called by the MIDlet when resuming from pauseApp, to re-assert UI state. */
    public String statusLine() {
        StringBuffer sb = new StringBuffer(60);
        sb.append(server.getHost());
        sb.append(':');
        sb.append(server.getPort());
        if (server.isSecure()) {
            sb.append('+');
        }
        sb.append(' ');
        sb.append(server.getStateName());
        long lag = server.getLagMs();
        if (lag >= 0) {
            sb.append(" ");
            sb.append(server.lagText());
        }
        return sb.toString();
    }
}
