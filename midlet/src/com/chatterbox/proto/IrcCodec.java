package com.chatterbox.proto;

import java.util.Vector;

/**
 * Serialises protocol lines. Every outbound line is built here so that the
 * 512-byte limit, the ":" escaping and the trailing-parameter placement are
 * enforced in exactly one place.
 *
 * Assumptions:
 *  - We always send CRLF (RFC 2812). Every IRC server since 1993 accepts it.
 *  - Line budget is 512 bytes *including* CRLF, measured in UTF-8 bytes since
 *    that is what the server counts.
 *  - {@link #truncate} shortens the trailing rather than silently emitting an
 *    over-long line, because a server will kill the connection otherwise.
 */
public final class IrcCodec {

    private IrcCodec() {
    }

    /** Build a line and return it with CRLF, truncated to 512 UTF-8 bytes. */
    public static String command(String command, Vector params, String trailing) {
        IrcLine l = new IrcLine(null, command, IrcLine.toArray(params), trailing);
        return line(l);
    }

    /** Same as {@link #command} but with a prefix (":nick!user@host"). */
    public static String prefixed(String prefix, String command, Vector params, String trailing) {
        IrcLine l = new IrcLine(prefix, command, IrcLine.toArray(params), trailing);
        return line(l);
    }

    /**
     * Serialise without CRLF (used by tests and the gateway log).
     */
    public static String serialize(IrcLine l) {
        StringBuffer sb = new StringBuffer(64);
        if (l.getPrefix() != null && l.getPrefix().length() > 0) {
            sb.append(':');
            sb.append(l.getPrefix());
            sb.append(' ');
        }
        sb.append(l.getCommand());
        String[] ps = l.getParams();
        if (ps != null) {
            for (int i = 0; i < ps.length; i++) {
                sb.append(' ');
                sb.append(ps[i]);
            }
        }
        if (l.getTrailing() != null) {
            sb.append(" :");
            sb.append(l.getTrailing());
        }
        return sb.toString();
    }

    /**
     * Serialise with CRLF, hard-truncating the trailing so the whole thing fits
     * in 512 UTF-8 bytes.
     */
    public static String line(IrcLine l) {
        String s = serialize(l);
        byte[] b = Utf8.encode(s);
        if (b.length <= IrcLine.MAX_LINE_BYTES - 2) {
            return s + "\r\n";
        }
        // Over budget. Shrink the trailing parameter (or the command if there
        // is no trailing) until it fits.
        String cmd = l.getCommand();
        String prefix = l.getPrefix();
        String[] ps = l.getParams();
        String trailing = l.getTrailing();

        for (int cut = 1; cut < 4096; cut++) {
            if (trailing != null && trailing.length() > cut) {
                trailing = trailing.substring(0, trailing.length() - cut);
            } else if (trailing != null) {
                trailing = "";
            } else if (ps != null) {
                ps = shrinkParams(ps);
            } else if (prefix != null && prefix.length() > cut) {
                prefix = prefix.substring(0, prefix.length() - cut);
            } else {
                break;
            }
            IrcLine t = new IrcLine(prefix, cmd, ps, trailing);
            byte[] tb = Utf8.encode(serialize(t));
            if (tb.length <= IrcLine.MAX_LINE_BYTES - 2) {
                return serialize(t) + "\r\n";
            }
        }
        // Last resort: bare, minimal line.
        return cmd + "\r\n";
    }

    private static String[] shrinkParams(String[] ps) {
        if (ps.length == 0) {
            return null;
        }
        String[] out = new String[ps.length - 1];
        System.arraycopy(ps, 0, out, 0, out.length);
        return out;
    }

    // ---- Convenience builders used by Session / CommandProcessor ----

    public static String nick(String nick) {
        return command("NICK", null, nick);
    }

    public static String user(String user, String mode, String unused, String real) {
        Vector v = new Vector();
        v.addElement(user);
        v.addElement(mode == null || mode.length() == 0 ? "0" : mode);
        v.addElement(unused == null ? "*" : unused);
        return command("USER", v, real);
    }

    public static String pass(String password) {
        return command("PASS", null, password);
    }

    public static String ping(String token) {
        return command("PING", null, token);
    }

    public static String pong(String token) {
        return command("PONG", null, token);
    }

    public static String pong(String server, String token) {
        Vector v = new Vector();
        v.addElement(server);
        return command("PONG", v, token);
    }

    public static String join(String channel) {
        return command("JOIN", null, channel);
    }

    /** RFC 2812: the key is a middle parameter, not a trailing. */
    public static String join(String channel, String key) {
        Vector v = new Vector();
        v.addElement(channel);
        v.addElement(key);
        return command("JOIN", v, null);
    }

    public static String part(String channel, String reason) {
        Vector v = new Vector();
        v.addElement(channel);
        return command("PART", v, reason);
    }

    public static String privmsg(String target, String text) {
        Vector v = new Vector();
        v.addElement(target);
        return command("PRIVMSG", v, text);
    }

    public static String notice(String target, String text) {
        Vector v = new Vector();
        v.addElement(target);
        return command("NOTICE", v, text);
    }

    public static String quit(String reason) {
        return command("QUIT", null, reason);
    }

    public static String topic(String channel, String newTopic) {
        Vector v = new Vector();
        v.addElement(channel);
        if (newTopic == null) {
            return command("TOPIC", v, null);
        }
        return command("TOPIC", v, newTopic);
    }

    public static String mode(String target, String flags) {
        Vector v = new Vector();
        v.addElement(target);
        if (flags != null) {
            v.addElement(flags);
        }
        return command("MODE", v, null);
    }

    public static String kick(String channel, String nick, String reason) {
        Vector v = new Vector();
        v.addElement(channel);
        v.addElement(nick);
        return command("KICK", v, reason);
    }

    public static String whois(String nick) {
        return command("WHOIS", null, nick);
    }

    public static String who(String mask) {
        Vector v = new Vector();
        v.addElement(mask == null ? "0" : mask);
        return command("WHO", v, null);
    }

    public static String names(String channel) {
        Vector v = new Vector();
        v.addElement(channel);
        return command("NAMES", v, null);
    }

    public static String ison(String nick) {
        return command("ISON", null, nick);
    }

    /** /me — CTCP ACTION. */
    public static String action(String target, String text) {
        return privmsg(target, Ctcp.wrap(Ctcp.ACTION, text));
    }

    /** CTCP VERSION reply to an inbound VERSION query. */
    public static String versionReply(String target, String version) {
        return notice(target, Ctcp.wrap(Ctcp.VERSION, version));
    }
}
