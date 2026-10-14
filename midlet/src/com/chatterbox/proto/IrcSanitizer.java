package com.chatterbox.proto;

/**
 * IRC protocol sanitization utilities.
 *
 * Prevents command injection by sanitizing user input that will be placed
 * into IRC protocol lines. IRC uses spaces as parameter delimiters and
 * a leading ':' to indicate a trailing parameter that can contain spaces.
 * An attacker could inject new parameters or commands by including these
 * characters in their input.
 */
public final class IrcSanitizer {

    private IrcSanitizer() {
    }

    /**
     * Sanitize a string for use as a single IRC middle parameter.
     * Replaces spaces, colons, and control characters that could break
     * the protocol framing.
     *
     * @param input the user-provided string
     * @return sanitized string safe for use as a middle parameter
     */
    public static String sanitizeParam(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Allow printable ASCII (0x20-0x7E) except space (0x20), colon (0x3A), and backslash
            if (c >= 0x21 && c <= 0x7E && c != ':' && c != '\\' && c != '\r' && c != '\n') {
                sb.append(c);
            }
            // Replace problematic chars with underscore
            else if (c == ' ' || c == ':' || c == '\\' || c == '\r' || c == '\n') {
                sb.append('_');
            }
            // Drop control characters
        }
        // IRC nick/channel limits: middle params max ~200 chars
        if (sb.length() > 200) {
            sb.setLength(200);
        }
        return sb.toString();
    }

    /**
     * Sanitize a string for use as an IRC trailing parameter.
     * Trailing parameters can contain spaces but not CRLF or null bytes.
     *
     * @param input the user-provided string
     * @return sanitized string safe for use as a trailing parameter
     */
    public static String sanitizeTrailing(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Allow printable ASCII and space, but not CRLF or null
            if (c >= 0x20 && c <= 0x7E && c != '\r' && c != '\n') {
                sb.append(c);
            }
            // Replace CRLF with space
            else if (c == '\r' || c == '\n') {
                sb.append(' ');
            }
            // Drop other control characters
        }
        // IRC line limit is 512 bytes including command and params
        // Trailing should leave room for prefix + command + middle params + " :" + CRLF
        // Conservative limit: 400 chars
        if (sb.length() > 400) {
            sb.setLength(400);
        }
        return sb.toString();
    }

    /**
     * Sanitize a channel name.
     * Valid channel names start with # or & followed by non-space, non-comma, non-colon chars.
     */
    public static String sanitizeChannel(String input) {
        if (input == null) {
            return "";
        }
        String s = input.trim();
        // Must start with # or &
        if (s.length() == 0 || (s.charAt(0) != '#' && s.charAt(0) != '&')) {
            s = "#" + s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0) {
                sb.append(c); // Keep # or &
            } else if (c >= 0x21 && c <= 0x7E && c != ',' && c != ':' && c != ' ') {
                sb.append(c);
            }
        }
        // Channel name limit is 200 chars
        if (sb.length() > 200) {
            sb.setLength(200);
        }
        return sb.toString();
    }

    /**
     * Sanitize a nickname.
     * Valid IRC nicks: start with letter/[\]_^{|}`, then alphanum/[\]_^{|}`-
     */
    public static String sanitizeNick(String input) {
        if (input == null) {
            return "";
        }
        String s = input.trim();
        StringBuilder sb = new StringBuilder(s.length());
        boolean first = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok;
            if (first) {
                ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || c == '[' || c == ']' || c == '_' || c == '^'
                        || c == '{' || c == '}' || c == '|' || c == '`' || c == '\\';
            } else {
                ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9')
                        || c == '[' || c == ']' || c == '_' || c == '^'
                        || c == '{' || c == '}' || c == '|' || c == '`' || c == '\\'
                        || c == '-';
            }
            if (ok) {
                sb.append(c);
                first = false;
            }
        }
        // Nick limit is 30 chars (some networks 31)
        if (sb.length() > 30) {
            sb.setLength(30);
        }
        return sb.toString();
    }

    /**
     * Sanitize text for PRIVMSG/NOTICE (trailing parameter).
     * Allows spaces and most printable chars, but strips CRLF and limits length.
     */
    public static String sanitizeMessage(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 0x20 && c <= 0x7E) || c == '\t') {
                sb.append(c);
            } else if (c == '\r' || c == '\n') {
                sb.append(' ');
            }
            // Drop other control characters
        }
        // PRIVMSG trailing limit: 512 - len(":nick!user@host PRIVMSG #chan :") - 2
        // Conservative: 400 chars
        if (sb.length() > 400) {
            sb.setLength(400);
        }
        return sb.toString();
    }

    /**
     * Sanitize a CTCP message body.
     * CTCP uses DEL (0x01) as delimiter, so strip it.
     */
    public static String sanitizeCtcp(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == 0x01 || c == 0x02 || c == '\r' || c == '\n') {
                // Drop CTCP delimiters and CRLF
                continue;
            }
            if (c >= 0x20 && c <= 0x7E) {
                sb.append(c);
            }
        }
        if (sb.length() > 200) {
            sb.setLength(200);
        }
        return sb.toString();
    }
}