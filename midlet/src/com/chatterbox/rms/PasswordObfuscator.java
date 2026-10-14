package com.chatterbox.rms;

/**
 * Simple password obfuscation for RMS storage.
 *
 * This is NOT encryption - it's XOR obfuscation with a per-device key.
 * It prevents casual reading of RMS records but will not stop a determined
 * attacker with device access. CLDC 1.1 does not provide crypto APIs.
 *
 * The key is derived from device-specific properties to make it unique
 * per installation, preventing rainbow tables across devices.
 */
public final class PasswordObfuscator {

    private static final String OBFUSCATION_PREFIX = "OBF:";
    private static volatile String deviceKey = null;

    private PasswordObfuscator() {
    }

    /**
     * Get or generate a device-specific obfuscation key.
     * Uses system properties that are stable per-installation but vary across devices.
     */
    private static synchronized String getDeviceKey() {
        if (deviceKey != null) {
            return deviceKey;
        }
        StringBuilder sb = new StringBuilder(64);
        // MicroEdition platform properties (vary by device/firmware)
        sb.append(System.getProperty("microedition.platform", ""));
        sb.append(System.getProperty("microedition.configuration", ""));
        sb.append(System.getProperty("microedition.profile", ""));
        sb.append(System.getProperty("microedition.locale", ""));
        sb.append(System.getProperty("user.timezone", ""));
        // Add a fixed salt to prevent trivial reversal
        sb.append("Chatterbox2026");
        deviceKey = sb.toString();
        return deviceKey;
    }

    /**
     * XOR-based obfuscation. Reversible with the same key.
     */
    private static String xorObfuscate(String input, String key) {
        if (input == null || input.length() == 0) {
            return "";
        }
        char[] in = input.toCharArray();
        char[] k = key.toCharArray();
        char[] out = new char[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (char) (in[i] ^ k[i % k.length]);
        }
        return new String(out);
    }

    /**
     * Obfuscate a password for storage.
     * @param plaintext the password to obfuscate
     * @return obfuscated string prefixed with "OBF:", or empty string if input is empty
     */
    public static String obfuscate(String plaintext) {
        if (plaintext == null || plaintext.length() == 0) {
            return "";
        }
        String key = getDeviceKey();
        String obf = xorObfuscate(plaintext, key);
        // Encode as hex to avoid character encoding issues in RMS
        return OBFUSCATION_PREFIX + toHex(obf.getBytes());
    }

    /**
     * Deobfuscate a password from storage.
     * @param obfuscated the obfuscated string (with or without "OBF:" prefix)
     * @return the original password, or empty string if deobfuscation fails
     */
    public static String deobfuscate(String obfuscated) {
        if (obfuscated == null || obfuscated.length() == 0) {
            return "";
        }
        String data = obfuscated;
        if (data.startsWith(OBFUSCATION_PREFIX)) {
            data = data.substring(OBFUSCATION_PREFIX.length());
        } else {
            // Legacy plaintext password - return as-is for migration
            return obfuscated;
        }
        try {
            byte[] bytes = fromHex(data);
            String obf = new String(bytes);
            String key = getDeviceKey();
            return xorObfuscate(obf, key);
        } catch (Exception e) {
            // Corrupted or tampered data - return empty
            return "";
        }
    }

    /**
     * Check if a string appears to be obfuscated.
     */
    public static boolean isObfuscated(String s) {
        return s != null && s.startsWith(OBFUSCATION_PREFIX);
    }

    // Hex encoding/decoding (no java.util.Base64 in CLDC 1.1)
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >> 4];
            out[i * 2 + 1] = HEX[b & 0xF];
        }
        return new String(out);
    }

    private static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd hex length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            char high = hex.charAt(i * 2);
            char low = hex.charAt(i * 2 + 1);
            int val = (hexVal(high) << 4) | hexVal(low);
            out[i] = (byte) val;
        }
        return out;
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("Invalid hex char: " + c);
    }
}