package com.chatterbox.core;

import java.util.Vector;

/**
 * Whois cache to avoid repeated queries for the same nick.
 * Stores whois results with TTL (default 5 minutes).
 */
public final class WhoisCache {

    private static final long DEFAULT_TTL = 5 * 60 * 1000; // 5 minutes

    private final Vector entries = new Vector();
    private final long ttl;

    public WhoisCache() {
        this(DEFAULT_TTL);
    }

    public WhoisCache(long ttlMillis) {
        this.ttl = ttlMillis;
    }

    public static final class Entry {
        public String nick;
        public String user;
        public String host;
        public String realName;
        public String server;
        public String idle;
        public String signon;
        public String channels;
        public long timestamp;

        public boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    public synchronized void put(Entry e) {
        if (e == null) {
            return;
        }
        // Remove existing entry for same nick (case-insensitive)
        for (int i = 0; i < entries.size(); i++) {
            Entry ex = (Entry) entries.elementAt(i);
            if (IrcCase.same(ex.nick, e.nick)) {
                entries.removeElementAt(i);
                break;
            }
        }
        e.timestamp = System.currentTimeMillis();
        entries.addElement(e);
        // Cap at 100 entries
        if (entries.size() > 100) {
            entries.removeElementAt(0);
        }
    }

    public synchronized Entry get(String nick) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = (Entry) entries.elementAt(i);
            if (IrcCase.same(e.nick, nick)) {
                if (e.isExpired(ttl)) {
                    entries.removeElementAt(i);
                    return null;
                }
                return e;
            }
        }
        return null;
    }

    public synchronized void clear() {
        entries.removeAllElements();
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Build a displayable string from cache entry. */
    public static String format(Entry e) {
        if (e == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(128);
        sb.append("Whois for ").append(e.nick).append('\n');
        if (e.user != null && e.host != null) {
            sb.append("  ").append(e.user).append("@").append(e.host).append('\n');
        }
        if (e.realName != null && e.realName.length() > 0) {
            sb.append("  Real name: ").append(e.realName).append('\n');
        }
        if (e.server != null) {
            sb.append("  Server: ").append(e.server).append('\n');
        }
        if (e.idle != null) {
            sb.append("  Idle: ").append(e.idle).append('\n');
        }
        if (e.signon != null) {
            sb.append("  Signed on: ").append(e.signon).append('\n');
        }
        if (e.channels != null && e.channels.length() > 0) {
            sb.append("  Channels: ").append(e.channels).append('\n');
        }
        return sb.toString();
    }
}