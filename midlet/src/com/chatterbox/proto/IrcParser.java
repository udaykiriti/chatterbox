package com.chatterbox.proto;

import java.util.Vector;

/**
 * Parses a single IRC line into an {@link IrcLine}.
 *
 * Pure Java, no UI imports, no MicroEdition imports -- unit-testable on desktop.
 *
 * Assumptions:
 *  - Input may or may not still carry a trailing CR; it is stripped here.
 *  - Parsing never throws. Anything unparseable yields null so the caller can
 *    drop the line and keep the connection alive.
 *  - A token is a MIDDLE parameter unless it is introduced by ':', in which case
 *    it is the TRAILING and absorbs spaces. This is the single most common
 *    parser bug in IRC clients, so it is worth stating explicitly: "MODE #c +o
 *    nick" has TWO middle params and NO trailing, while "MODE #c +o :nick" has
 *    two params and a trailing.
 *  - A command longer than 15 chars is not a command; truncate rather than
 *    resynchronise blindly.
 */
public final class IrcParser {

    private IrcParser() {
    }

    public static IrcLine parse(String raw) {
        if (raw == null) {
            return null;
        }
        String line = stripEol(raw);
        int len = line.length();
        if (len == 0) {
            return null;
        }

        int i = 0;
        String prefix = null;
        if (line.charAt(0) == ':') {
            int sp = line.indexOf(' ');
            if (sp < 0) {
                return null;
            }
            prefix = line.substring(1, sp);
            if (prefix.length() == 0) {
                prefix = null;
            }
            i = sp + 1;
        }

        int start = i;
        while (i < len && line.charAt(i) != ' ') {
            i++;
        }
        if (i == start) {
            return null;
        }
        String command = line.substring(start, i);
        if (command.length() > 15) {
            command = command.substring(0, 15);
        }
        command = command.toUpperCase();

        Vector mid = new Vector(IrcLine.MAX_PARAMS);
        String trailing = null;
        while (true) {
            while (i < len && line.charAt(i) == ' ') {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (line.charAt(i) == ':') {
                trailing = line.substring(i + 1);
                break;
            }
            start = i;
            while (i < len && line.charAt(i) != ' ') {
                i++;
            }
            if (mid.size() >= IrcLine.MAX_PARAMS) {
                // Absorb the tail into the trailing so we cannot overrun the
                // heap on a hostile or desynchronised stream. The current
                // token is included so nothing is silently dropped.
                trailing = line.substring(start);
                break;
            }
            mid.addElement(line.substring(start, i));
        }

        return new IrcLine(prefix, command, IrcLine.toArray(mid), trailing);
    }

    /**
     * Drop a trailing CRLF, LF or CR. LineReader already does this for the
     * socket path, but parse() is also fed directly by tests and by the gateway
     * debug view, and a stray newline silently corrupts the trailing parameter.
     */
    private static String stripEol(String s) {
        if (s == null) {
            return null;
        }
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '\r' || c == '\n') {
                end--;
            } else {
                break;
            }
        }
        return end == s.length() ? s : s.substring(0, end);
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.length() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static boolean isCommand(String s, String name) {
        return s != null && s.equalsIgnoreCase(name);
    }
}
