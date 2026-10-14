package com.chatterbox.core;

import java.util.Vector;

import com.chatterbox.net.ConnectionConfig;
import com.chatterbox.net.IrcConnection;
import com.chatterbox.rms.LogStore;
import com.chatterbox.rms.Profile;

/**
 * Owns every {@link IrcConnection} and routes events to a single UI listener.
 *
 * Rationale for a manager rather than a connection-per-MIDlet: a mobile user
 * commonly keeps two servers up (a network and a work one) and the UI has to
 * present them in one list with per-server badges.
 *
 * Threading: IrcConnection callbacks arrive on that connection's reader thread.
 * The manager re-dispatches immediately to the registered listener; the UI
 * listener is responsible for marshalling to the MIDlet thread. Keeping the
 * manager itself free of any UI knowledge is what lets this class be tested.
 */
public final class SessionManager implements IrcEventListener {

    private final Vector connections = new Vector();
    private IrcEventListener listener;
    private boolean logEnabled;

    public void setListener(IrcEventListener l) {
        this.listener = l;
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            ((IrcConnection) copy.elementAt(i)).setListener(this);
        }
    }

    public void setLogEnabled(boolean b) {
        logEnabled = b;
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            log((IrcConnection) copy.elementAt(i)).setEnabled(b);
        }
    }

    private Vector snapshot() {
        Vector v = new Vector(connections.size());
        for (int i = 0; i < connections.size(); i++) {
            v.addElement(connections.elementAt(i));
        }
        return v;
    }

    public synchronized int size() {
        return connections.size();
    }

    public synchronized IrcConnection connection(int i) {
        if (i < 0 || i >= connections.size()) {
            return null;
        }
        return (IrcConnection) connections.elementAt(i);
    }

    public synchronized IrcConnection byServerId(String id) {
        for (int i = 0; i < connections.size(); i++) {
            IrcConnection c = (IrcConnection) connections.elementAt(i);
            if (c.getServer().getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public synchronized void add(IrcConnection c) {
        if (byServerId(c.getServer().getId()) != null) {
            return;
        }
        connections.addElement(c);
        c.setListener(this);
        log(c).setEnabled(logEnabled);
    }

    /** Build and register a connection from a profile without starting it. */
    public IrcConnection create(Profile p, ConnectionConfig config) {
        ServerState s = new ServerState(p.serverId(), p.getHost(), p.getPort(), p.isSecure());
        s.setNick(p.getNick());
        s.setAltNick(p.getAltNick());
        s.setPassword(p.getPassword());
        s.setRealName(p.getRealName());
        s.setChannelList(p.getChannels());
        IrcConnection c = new IrcConnection(s, config);
        c.getSession().setAutoJoin(p.getChannels());
        add(c);
        return c;
    }

    public synchronized void remove(IrcConnection c) {
        if (c == null) {
            return;
        }
        connections.removeElement(c);
    }

    public synchronized void startAll() {
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            IrcConnection c = (IrcConnection) copy.elementAt(i);
            if (!c.isRunning()) {
                c.start();
            }
        }
    }

    public synchronized void stopAll(String reason) {
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            try {
                ((IrcConnection) copy.elementAt(i)).stopGracefully(reason);
            } catch (Throwable t) {
                // Never let one bad connection block the others from shutting down.
            }
        }
    }

    public synchronized void flushLogs() {
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            log((IrcConnection) copy.elementAt(i)).flush();
        }
    }

    private LogStore log(IrcConnection c) {
        return c.getLog();
    }

    /** Total unread across every server, for the app title. */
    public synchronized int totalUnread() {
        int n = 0;
        for (int i = 0; i < connections.size(); i++) {
            n += ((IrcConnection) connections.elementAt(i)).getServer().totalUnread();
        }
        return n;
    }

    public synchronized int totalHighlights() {
        int n = 0;
        for (int i = 0; i < connections.size(); i++) {
            n += ((IrcConnection) connections.elementAt(i)).getServer().totalHighlights();
        }
        return n;
    }

    public boolean anyConnecting() {
        Vector copy = snapshot();
        for (int i = 0; i < copy.size(); i++) {
            IrcConnection c = (IrcConnection) copy.elementAt(i);
            if (c.isRunning() && !c.getSession().isRegistered()) {
                return true;
            }
        }
        return false;
    }

    /** The most recently active window with a highlight, for jump-to-alert. */
    public synchronized ChannelState hottest() {
        ChannelState best = null;
        int bestCount = 0;
        for (int i = 0; i < connections.size(); i++) {
            ServerState s = ((IrcConnection) connections.elementAt(i)).getServer();
            Vector names = s.windowNames();
            for (int j = 0; j < names.size(); j++) {
                ChannelState w = s.window((String) names.elementAt(j));
                if (w != null && w.getHighlight() > bestCount) {
                    bestCount = w.getHighlight();
                    best = w;
                }
            }
        }
        return best;
    }

    // ---- IrcEventListener: pure forwarding ----

    private IrcEventListener delegate() {
        return listener;
    }

    private void logMessage(IrcConnection c, ServerState s, ChannelState w, Message m) {
        log(c).log(s, w, m);
    }

    public void onMessage(ServerState s, ChannelState w, Message m) {
        IrcConnection c = byServerId(s.getId());
        if (c != null) {
            logMessage(c, s, w, m);
        }
        IrcEventListener d = delegate();
        if (d != null) {
            d.onMessage(s, w, m);
        }
    }

    public void onStateChanged(ServerState s, int state, String text) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onStateChanged(s, state, text);
        }
    }

    public void onNickInUse(ServerState s, String attempted, String chosen) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onNickInUse(s, attempted, chosen);
        }
    }

    public void onWindowOpened(ServerState s, ChannelState w) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onWindowOpened(s, w);
        }
    }

    public void onWindowClosed(ServerState s, ChannelState w) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onWindowClosed(s, w);
        }
    }

    public void onTopicChanged(ServerState s, ChannelState c, String topic) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onTopicChanged(s, c, topic);
        }
    }

    public void onError(ServerState s, String reason) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onError(s, reason);
        }
    }

    public void onAlert(ServerState s, ChannelState w, boolean mention, boolean priv) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onAlert(s, w, mention, priv);
        }
    }

    public void onLag(ServerState s, long millis) {
        IrcEventListener d = delegate();
        if (d != null) {
            d.onLag(s, millis);
        }
    }
}
