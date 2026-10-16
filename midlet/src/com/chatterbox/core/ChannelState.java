package com.chatterbox.core;

import java.util.Vector;

/**
 * Per-channel state: topic, mode string, nick list, history ring and counters.
 *
 * Assumptions:
 *  - Nicks are held in a sorted Vector of {@link UserEntry} because channel
 *    lists are short (tens to low hundreds) and a binary-search insert keeps
 *    the nick list screen stable without re-sorting on every join/part.
 *  - unread counts are capped at 999 so the badge never overflows the layout.
 *  - highlight is a subset of unread; a highlighted line bumps both.
 */
public final class ChannelState {

    public static final int TYPE_CHANNEL = 0;
    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_STATUS = 2;

    private final String name;
    private final int type;
    private final boolean caseSensitive;

    private String topic = "";
    private String topicBy = "";
    private long topicTime;
    private String modes = "";
    private String key = "";

    private final Vector users = new Vector();
    private final MessageRing ring;

    private int unread;
    private int highlight;
    private boolean active;
    private boolean open;
    private boolean parted;
    private long lastActivity;

    /** Nick that started a private conversation, for tab titles. */
    private String peerNick = "";

    public ChannelState(String name, int type) {
        this(name, type, MessageRing.DEFAULT_CAPACITY);
    }

    public ChannelState(String name, int type, int capacity) {
        this.name = name == null ? "" : name;
        this.type = type;
        this.ring = new MessageRing(capacity);
        // Channels compare case-insensitively per RFC 2812; some networks
        // (efnet) are case-preserving but case-insensitive for lookup.
        this.caseSensitive = false;
        this.lastActivity = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public boolean isPrivate() {
        return type == TYPE_PRIVATE;
    }

    public boolean isChannel() {
        return type == TYPE_CHANNEL;
    }

    public MessageRing getRing() {
        return ring;
    }

    // ---- topic / modes ----

    public String getTopic() {
        return topic;
    }

    public void setTopic(String t, String by, long when) {
        this.topic = t == null ? "" : t;
        this.topicBy = by == null ? "" : by;
        this.topicTime = when;
    }

    public String getTopicBy() {
        return topicBy;
    }

    public long getTopicTime() {
        return topicTime;
    }

    public String getModes() {
        return modes;
    }

    public void setModes(String m) {
        this.modes = m == null ? "" : m;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String k) {
        this.key = k == null ? "" : k;
    }

    // ---- users ----

    /** @return the entry, creating it if absent. Never null. */
    public synchronized UserEntry user(String nick, boolean create) {
        String n = IrcCase.normalize(nick);
        int idx = indexOfUser(n);
        if (idx >= 0) {
            return (UserEntry) users.elementAt(idx);
        }
        if (!create) {
            return null;
        }
        UserEntry e = new UserEntry(nick);
        insertUser(e);
        return e;
    }

    public synchronized boolean removeUser(String nick) {
        int idx = indexOfUser(IrcCase.normalize(nick));
        if (idx < 0) {
            return false;
        }
        users.removeElementAt(idx);
        return true;
    }

    /**
     * Apply a prefix mode change from "MODE #c +o nick".
     *
     * The server sends the MODE letter (o, v, h, a, q, y) but the NAMES reply
     * shows a PREFIX character (@, +, %, &amp;, ~, !). Networks may customise both
     * via the PREFIX= and STATUSMSG= ISUPPORT tokens; we use the defaults, which
     * every mainstream network honours.
     */
    public synchronized boolean applyPrefixMode(String nick, String modes) {
        UserEntry e = user(nick, false);
        if (e == null || modes == null) {
            return false;
        }
        boolean add = true;
        for (int i = 0; i < modes.length(); i++) {
            char c = modes.charAt(i);
            if (c == '+') {
                add = true;
            } else if (c == '-') {
                add = false;
            } else {
                if (add) {
                    e.addPrefixMode(String.valueOf(c)); // pass the MODE letter
                } else {
                    e.removePrefixMode(String.valueOf(c));
                }
            }
        }
        return true;
    }

    /** MODE letter -> NAMES prefix character. */
    public static char prefixCharFor(char mode) {
        switch (mode) {
            case 'q': return '~';
            case 'a': return '&';
            case 'o': return '@';
            case 'h': return '%';
            case 'v': return '+';
            case 'y': return '!';
            default: return mode;
        }
    }

    /** Rename in place, preserving sort order and prefix. */
    public synchronized boolean renameUser(String from, String to) {
        int idx = indexOfUser(IrcCase.normalize(from));
        if (idx < 0) {
            return false;
        }
        UserEntry e = (UserEntry) users.elementAt(idx);
        UserEntry fresh = new UserEntry(to);
        fresh.setPrefix(e.getPrefix());
        fresh.setIdent(e.getUser(), e.getHost(), e.getRealName());
        fresh.setAway(e.isAway());
        fresh.setSelf(e.isSelf());
        users.removeElementAt(idx);
        insertUser(fresh);
        return true;
    }

    public synchronized UserEntry findUser(String nick) {
        return user(nick, false);
    }

    public synchronized int userCount() {
        return users.size();
    }

    public synchronized Vector snapshotUsers() {
        Vector v = new Vector(users.size());
        for (int i = 0; i < users.size(); i++) {
            v.addElement(users.elementAt(i));
        }
        return v;
    }

    private int indexOfUser(String normalized) {
        int lo = 0;
        int hi = users.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            String c = ((UserEntry) users.elementAt(mid)).getFolded();
            int d = c.compareTo(normalized);
            if (d == 0) {
                return mid;
            }
            if (d < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -(lo + 1);
    }

    private void insertUser(UserEntry e) {
        int at = indexOfUser(e.getFolded());
        if (at >= 0) {
            users.setElementAt(e, at);
            return;
        }
        users.insertElementAt(e, -(at + 1));
    }

    // ---- counters ----

    public synchronized int getUnread() {
        return unread;
    }

    public synchronized int getHighlight() {
        return highlight;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public void setActive(boolean a) {
        this.active = a;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean o) {
        this.open = o;
    }

    public boolean isParted() {
        return parted;
    }

    public void setParted(boolean p) {
        this.parted = p;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public String getPeerNick() {
        return peerNick;
    }

    public void setPeerNick(String n) {
        this.peerNick = n == null ? "" : n;
    }

    /**
     * Record that a message arrived while the user was looking elsewhere.
     * Active windows never accumulate unread counts.
     */
    public void markUnread(boolean highlighted) {
        synchronized (this) {
            if (active) {
                return;
            }
            if (highlighted) {
                highlight = Math.min(999, highlight + 1);
                unread = Math.min(999, unread + 1);
            } else {
                unread = Math.min(999, unread + 1);
            }
        }
        lastActivity = System.currentTimeMillis();
    }

    public synchronized void clearCounters() {
        unread = 0;
        highlight = 0;
    }

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    /** Badge text for the server list, or "" when there is nothing to show. */
    public String badge() {
        synchronized (this) {
            if (highlight > 0) {
                return "!" + highlight;
            }
            if (unread > 0) {
                return String.valueOf(unread);
            }
        }
        return "";
    }

    public String toString() {
        return name;
    }
}
