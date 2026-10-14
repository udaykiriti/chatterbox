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

    /** Decode base64 server challenge (not used for PLAIN/EXTERNAL). */
    public static byte[] decodeBase64(String s) {
        // Minimal decoder for future SCRAM support. Returns empty for now.
        return new byte[0];
    }

    /** Check if mechanism is supported by this client. */
    public static boolean isSupported(String mech) {
        return MECH_PLAIN.equalsIgnoreCase(mech) || MECH_EXTERNAL.equalsIgnoreCase(mech);
    }
}