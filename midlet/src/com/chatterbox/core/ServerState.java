package com.chatterbox.core;

import java.util.Vector;

/**
 * Everything we know about one IRC server connection's logical state.
 *
 * Pure data + accessors; no threading of its own beyond synchronization on the
 * channel map.
 */
public final class ServerState {

    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int REGISTERING = 2;
    public static final int CONNECTED = 3;
    public static final int RECONNECTING = 4;

    public static final String[] STATE_NAMES = {
        "offline", "connecting", "registering", "online", "retrying"
    };

    private final String id;          // stable key, e.g. "irc.example.net:6697"
    private String host;
    private int port;
    private boolean secure;

    private String nick = "";
    private String altNick = "";
    private String password = "";
    private String realName = "";
    private String channels = "";    // comma separated, for the settings form
    private boolean e2eOn;           // pre-shared-key E2E toggle
    private String e2eKey = "";      // E2E passphrase (empty = E2E off)

    private String selfNick = "";
    private String selfUser = "";
    private String selfHost = "";
    private String motd = "";

    private int state = DISCONNECTED;
    private long connectedAt;
    private long lagMs = -1;
    private String statusText = "";

    private final java.util.Hashtable windows = IrcCase.newMap();
    private Vector channelOrder = new Vector();
    private ChannelState active;

    public ServerState(String id, String host, int port, boolean secure) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.secure = secure;
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String n) {
        this.nick = n == null ? "" : n;
    }

    public String getAltNick() {
        return altNick;
    }

    public void setAltNick(String n) {
        this.altNick = n == null ? "" : n;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String p) {
        this.password = p == null ? "" : p;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String r) {
        this.realName = r == null ? "" : r;
    }

    public String getChannelList() {
        return channels;
    }

    public void setChannelList(String c) {
        this.channels = c == null ? "" : c;
    }

    /** Pre-shared-key end-to-end encryption on/off. */
    public boolean isE2eOn() {
        return e2eOn;
    }

    public void setE2eOn(boolean on) {
        this.e2eOn = on;
    }

    public String getE2eKey() {
        return e2eKey;
    }

    public void setE2eKey(String k) {
        this.e2eKey = k == null ? "" : k;
    }

    /** Both a key and the toggle are needed before anything is encrypted. */
    public boolean isE2eReady() {
        return e2eOn && e2eKey.length() > 0;
    }

    public String getSelfNick() {
        return selfNick;
    }

    public void setSelfNick(String n) {
        this.selfNick = n == null ? "" : n;
    }

    public String getSelfUser() {
        return selfUser;
    }

    public void setSelfUser(String u) {
        selfUser = u == null ? "" : u;
    }

    public String getSelfHost() {
        return selfHost;
    }

    public void setSelfHost(String h) {
        selfHost = h == null ? "" : h;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String m) {
        motd = m == null ? "" : m;
    }

    public synchronized int getState() {
        return state;
    }

    public synchronized void setState(int s) {
        if (s != state) {
            this.state = s;
            this.connectedAt = (s == CONNECTED) ? System.currentTimeMillis() : 0;
        }
    }

    public synchronized String getStateName() {
        return STATE_NAMES[state];
    }

    public synchronized void setStatusText(String t) {
        this.statusText = t == null ? "" : t;
    }

    public synchronized String getStatusText() {
        return statusText;
    }

    public synchronized void setConnectedAt(long t) {
        connectedAt = t;
    }

    public synchronized long getConnectedAt() {
        return connectedAt;
    }

    public synchronized void setLagMs(long ms) {
        lagMs = ms;
    }

    public synchronized long getLagMs() {
        return lagMs;
    }

    public synchronized String lagText() {
        if (lagMs < 0) {
            return "lag --";
        }
        if (lagMs < 1000) {
            return "lag " + lagMs + "ms";
        }
        return "lag " + (lagMs / 1000) + "s";
    }

    // ---- window registry ----

    public synchronized ChannelState window(String name) {
        return (ChannelState) windows.get(IrcCase.normalize(name));
    }

    /**
     * Get or create. Private windows get a 60-line ring, channels 200.
     */
    public synchronized ChannelState window(String name, int type) {
        String key = IrcCase.normalize(name);
        ChannelState c = (ChannelState) windows.get(key);
        if (c != null) {
            return c;
        }
        int cap = (type == ChannelState.TYPE_PRIVATE) ? 60 : MessageRing.DEFAULT_CAPACITY;
        c = new ChannelState(name, type, cap);
        if (type == ChannelState.TYPE_PRIVATE) {
            c.setPeerNick(com.chatterbox.proto.IrcLine.nickOf(name));
        }
        windows.put(key, c);
        channelOrder.addElement(name);
        return c;
    }

    public synchronized void closeWindow(String name) {
        String key = IrcCase.normalize(name);
        windows.remove(key);
        for (int i = 0; i < channelOrder.size(); i++) {
            if (IrcCase.normalize((String) channelOrder.elementAt(i)).equals(key)) {
                channelOrder.removeElementAt(i);
                break;
            }
        }
        if (active != null && IrcCase.normalize(active.getName()).equals(key)) {
            active = null;
        }
    }

    /** Channel names in join order, for the server list. */
    public synchronized Vector windowNames() {
        Vector v = new Vector(channelOrder.size());
        for (int i = 0; i < channelOrder.size(); i++) {
            v.addElement(channelOrder.elementAt(i));
        }
        return v;
    }

    public synchronized Vector channelWindows() {
        Vector v = new Vector();
        for (int i = 0; i < channelOrder.size(); i++) {
            String n = (String) channelOrder.elementAt(i);
            ChannelState c = window(n);
            if (c != null && c.isChannel()) {
                v.addElement(c);
            }
        }
        return v;
    }

    public synchronized ChannelState getActive() {
        return active;
    }

    public synchronized void setActive(ChannelState c) {
        for (java.util.Enumeration e = windows.elements(); e.hasMoreElements();) {
            ((ChannelState) e.nextElement()).setActive(false);
        }
        active = c;
        if (c != null) {
            c.setActive(true);
            c.clearCounters();
        }
    }

    public synchronized int totalUnread() {
        int n = 0;
        for (java.util.Enumeration e = windows.elements(); e.hasMoreElements();) {
            n += ((ChannelState) e.nextElement()).getUnread();
        }
        return n;
    }

    public synchronized int totalHighlights() {
        int n = 0;
        for (java.util.Enumeration e = windows.elements(); e.hasMoreElements();) {
            n += ((ChannelState) e.nextElement()).getHighlight();
        }
        return n;
    }

    /** Reset everything that only makes sense for a live connection. */
    public synchronized void onDisconnect() {
        setState(DISCONNECTED);
        for (java.util.Enumeration e = windows.elements(); e.hasMoreElements();) {
            ChannelState c = (ChannelState) e.nextElement();
            c.getRing().clear();
        }
        selfUser = "";
        selfHost = "";
        setLagMs(-1);
    }

    public String toString() {
        return host + ":" + port + (secure ? " (ssl)" : "");
    }
}
