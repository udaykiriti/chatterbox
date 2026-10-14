package com.chatterbox.core;

/**
 * A user in a channel or a private conversation.
 *
 * Assumptions:
 *  - We only track what we need to render a nick list and colour names:
 *    prefix modes, away flag, and the operator/voice bits that show as @ and +.
 *  - 'prefix' is the raw prefix characters from the server (@ + % & ~ !).
 *    Different networks use different prefix letters for the same rank, so we
 *    keep the letter, not a rank number.
 */
public final class UserEntry {

    private final String nick;
    private String prefix = "";
    private String user = "";
    private String host = "";
    private boolean away;
    private boolean self;
    private String realName = "";

    public UserEntry(String nick) {
        this.nick = nick == null ? "" : nick;
    }

    public String getNick() {
        return nick;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String p) {
        this.prefix = p == null ? "" : p;
    }

    public void addPrefixMode(String modes) {
        if (modes == null) {
            return;
        }
        for (int i = 0; i < modes.length(); i++) {
            char c = modes.charAt(i);
            if (c == '+' || c == '-') {
                continue;
            }
            char p = ChannelState.prefixCharFor(c);
            if (prefix.indexOf(p) < 0) {
                prefix = p + prefix;
            }
        }
    }

    public void removePrefixMode(String modes) {
        if (modes == null) {
            return;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < modes.length(); i++) {
            char c = modes.charAt(i);
            if (c == '+' || c == '-') {
                continue;
            }
            char p = ChannelState.prefixCharFor(c);
            for (int j = 0; j < prefix.length(); j++) {
                if (prefix.charAt(j) != p) {
                    sb.append(prefix.charAt(j));
                }
            }
            prefix = sb.toString();
            sb.setLength(0);
        }
    }

    public boolean isOp() {
        return prefix.indexOf('@') >= 0;
    }

    public boolean isVoiced() {
        return prefix.indexOf('+') >= 0;
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    public void setIdent(String user, String host, String real) {
        this.user = user == null ? "" : user;
        this.host = host == null ? "" : host;
        this.realName = real == null ? "" : real;
    }

    public String getRealName() {
        return realName;
    }

    public boolean isAway() {
        return away;
    }

    public void setAway(boolean a) {
        this.away = a;
    }

    public boolean isSelf() {
        return self;
    }

    public void setSelf(boolean s) {
        this.self = s;
    }

    /** Display form including prefix, e.g. "@bob". */
    public String display() {
        return prefix + nick;
    }

    /** Full form "nick!user@host", or just the nick when unknown. */
    public String fully() {
        if (user.length() == 0 && host.length() == 0) {
            return nick;
        }
        return nick + "!" + user + "@" + host;
    }

    public String toString() {
        return display();
    }
}