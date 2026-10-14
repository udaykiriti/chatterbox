package com.chatterbox.proto;

import java.util.Vector;

/**
 * One parsed IRC protocol line.
 *
 * Structure: [':' prefix SPACE] command [SPACE param]* [SPACE ':' trailing] CRLF
 *
 * Assumptions:
 *  - The trailing parameter is stored separately and is never also a member of
 *    {@link #params}. That invariant is maintained by {@link IrcParser} and by
 *    every {@link IrcCodec} builder, so consumers can rely on it.
 *  - The leading ':' and the CRLF are stripped by the parser, not stored here.
 *  - At most {@link #MAX_PARAMS} middle parameters; anything beyond that is
 *    folded into the trailing to bound memory on a 1 MB-heap MIDlet.
 */
public final class IrcLine {

    public static final int MAX_PARAMS = 15;
    /** RFC 1459/2812 line limit including CRLF. */
    public static final int MAX_LINE_BYTES = 512;

    private String prefix;
    private String command;
    private String[] params;
    private String trailing;
    /** Byte length of the raw line on the wire, before decoding. */
    private int rawBytes;

    public IrcLine(String prefix, String command, String[] params, String trailing) {
        this.prefix = prefix;
        this.command = command;
        this.params = params;
        this.trailing = trailing;
    }

    public IrcLine(String command) {
        this(null, command, null, null);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String c) {
        this.command = c;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] p) {
        this.params = p;
    }

    public String getTrailing() {
        return trailing;
    }

    public void setTrailing(String t) {
        this.trailing = t;
    }

    public int getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(int n) {
        this.rawBytes = n;
    }

    public int getParamCount() {
        return params == null ? 0 : params.length;
    }

    /**
     * @return param i, or the trailing if i is one past the end and a trailing
     *         exists, else null. This mirrors how most IRC code reads the last
     *         token of a command.
     */
    public String getParam(int i) {
        if (params != null && i < params.length) {
            return params[i];
        }
        if (i == getParamCount() && trailing != null) {
            return trailing;
        }
        return null;
    }

    /** 0-based, -1 when not present. */
    public int indexOfParam(String value) {
        if (params == null || value == null) {
            return -1;
        }
        for (int i = 0; i < params.length; i++) {
            if (value.equals(params[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Nick or server name of the prefix, i.e. everything before the first '!'.
     * Returns null when there is no prefix.
     */
    public String getPrefixNick() {
        if (prefix == null) {
            return null;
        }
        int bang = prefix.indexOf('!');
               if (bang >= 0) {
            return prefix.substring(0, bang);
        }
        int at = prefix.indexOf('@');
        if (at >= 0) {
            return prefix.substring(0, at);
        }
        return prefix;
    }

    /** The part after '!' in "nick!user@host", or null. */
    public String getPrefixUser() {
        if (prefix == null) {
            return null;
        }
        int bang = prefix.indexOf('!');
        if (bang < 0) {
            return null;
        }
        int at = prefix.indexOf('@', bang);
        if (at < 0) {
            return prefix.substring(bang + 1);
        }
        return prefix.substring(bang + 1, at);
    }

    /** The part after '@' in "nick!user@host", or null. */
    public String getPrefixHost() {
        if (prefix == null) {
            return null;
        }
        int at = prefix.indexOf('@');
        if (at < 0) {
            return null;
        }
        return prefix.substring(at + 1);
    }

    public boolean isFromServer() {
        // A server prefix has no '!' and no '@' AND contains a dot
        // ("irc.libera.chat"). A bare "nick" with no dot is a client, because
        // several ircds do not send user@host on JOIN for the local user.
        if (prefix == null) {
            return true;
        }
        return prefix.indexOf('!') < 0 && prefix.indexOf('@') < 0
                && prefix.indexOf('.') >= 0;
    }

    /**
     * Rebuild the wire form (no CRLF). Used by tests and by the gateway debug
     * log; the client never re-serialises received lines.
     */
    public String encode() {
        return IrcCodec.serialize(this);
    }

    public String toString() {
        return encode();
    }

    /** Convenience: split "nick!user@host" style names. */
    public static String nickOf(String full) {
        if (full == null) {
            return null;
        }
        int bang = full.indexOf('!');
        int at = full.indexOf('@');
        int end = full.length();
        if (bang >= 0 && bang < end) {
            end = bang;
        }
        if (at >= 0 && at < end) {
            end = at;
        }
        return full.substring(0, end);
    }

    static String[] toArray(Vector v) {
        if (v == null || v.size() == 0) {
            return null;
        }
        String[] a = new String[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = (String) v.elementAt(i);
        }
        return a;
    }
}
