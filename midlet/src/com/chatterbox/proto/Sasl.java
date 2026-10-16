package com.chatterbox.proto;

/**
 * SASL authentication helper.
 *
 * Handles PLAIN and EXTERNAL mechanisms. The MIDlet sends AUTHENTICATE
 * commands with base64-encoded payloads. Pure CLDC 1.1 — no java.util.Base64,
 * so we implement a tiny encoder.
 *
 * Assumptions:
 *  - Only PLAIN and EXTERNAL are implemented. SCRAM is too heavy for CLDC.
 *  - The server advertises mechanisms via CAP LS or 908 numeric (not yet
 *    parsed). The MIDlet settings screen lets the user pick.
 */
public final class Sasl {

    private Sasl() {
    }

    public static final String MECH_PLAIN = "PLAIN";
    public static final String MECH_EXTERNAL = "EXTERNAL";

    /** Encode to base64 (RFC 4648, no line wrapping). */
    public static String encodeBase64(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer((data.length * 4 + 2) / 3);
        int i = 0;
        while (i < data.length) {
            int b1 = data[i++] & 0xFF;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            int b3 = i < data.length ? data[i++] & 0xFF : -1;
            int n = (b1 << 16) | ((b2 >= 0 ? b2 : 0) << 8) | (b3 >= 0 ? b3 : 0);
            sb.append(ENC[(n >> 18) & 0x3F]);
            sb.append(ENC[(n >> 12) & 0x3F]);
            sb.append(b2 >= 0 ? ENC[(n >> 6) & 0x3F] : '=');
            sb.append(b3 >= 0 ? ENC[n & 0x3F] : '=');
        }
        return sb.toString();
    }

    private static final char[] ENC = {
        'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P',
        'Q','R','S','T','U','V','W','X','Y','Z','a','b','c','d','e','f',
        'g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v',
        'w','x','y','z','0','1','2','3','4','5','6','7','8','9','+','/'
    };

    /** Build PLAIN payload: NUL + authzid + NUL + password. */
    public static String plainPayload(String authzid, String authcid, String password) {
        StringBuffer sb = new StringBuffer();
        sb.append('\0');
        sb.append(authcid == null ? "" : authcid);
        sb.append('\0');
        sb.append(password == null ? "" : password);
        return encodeBase64(com.chatterbox.proto.Utf8.encode(sb.toString()));
    }

    /** EXTERNAL payload is empty (client cert auth). */
    public static String externalPayload() {
        return "";
    }

    /**
     * Decode base64 (RFC 4648). Lenient about missing padding and ignores
     * whitespace, but the third party feeding us these strings is a hostile
     * network, so malformed input returns null instead of throwing.
     */
    public static byte[] decodeBase64(String s) {
        if (s == null) {
            return null;
        }
        // Compact: drop whitespace; '=' only allowed in a trailing pad run.
        StringBuffer compact = new StringBuffer(s.length());
        int padChars = 0;
        boolean padSeen = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                continue;
            }
            if (c == '=') {
                if (compact.length() == 0) {
                    return null;            // leading '='
                }
                padSeen = true;
                padChars++;
                continue;
            }
            if (padSeen) {
                return null;                // data after padding
            }
            compact.append(c);
        }
        int body = compact.length();
        if (body == 0) {
            return new byte[0];
        }
        int rem = body % 4;
        if (rem == 1) {
            return null; // impossible grouping
        }
        // The pad run length must match the remainder exactly.
        if (padChars > 0 && padChars != 4 - rem) {
            return null;
        }
        int outLen = body / 4 * 3 + (rem == 0 ? 0 : rem == 2 ? 1 : 2);
        byte[] out = new byte[outLen];
        int acc = 0;
        int nbits = 0;
        int pos = 0;
        for (int i = 0; i < body; i++) {
            int v = valueOf(compact.charAt(i));
            if (v < 0) {
                return null;
            }
            acc = (acc << 6) | v;
            nbits += 6;
            if (nbits >= 8) {
                nbits -= 8;
                out[pos++] = (byte) ((acc >>> nbits) & 0xFF);
            }
        }
        return out;
    }

    private static int valueOf(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }

    /** Check if mechanism is supported by this client. */
    public static boolean isSupported(String mech) {
        return MECH_PLAIN.equalsIgnoreCase(mech) || MECH_EXTERNAL.equalsIgnoreCase(mech);
    }
}