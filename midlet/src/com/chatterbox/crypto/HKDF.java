package com.chatterbox.crypto;

/**
 * HMAC-based Key Derivation Function (HKDF) for CLDC 1.1.
 * 
 * Reference: RFC 5869, "HMAC-based Extract-and-Expand Key Derivation Function"
 * 
 * HKDF consists of two phases:
 *   1. Extract: HKDF-Extract(salt, IKM) -> PRK
 *   2. Expand: HKDF-Expand(PRK, info, L) -> OKM
 * 
 * Uses HMAC-SHA256 as the underlying PRF.
 */
public final class HKDF {

    private HKDF() {}

    private static final int HASH_LEN = 32; // SHA-256 output length

    /**
     * HKDF-Extract + HKDF-Expand in one call.
     * @param salt optional salt (can be null or empty)
     * @param ikm input keying material
     * @param info optional context/application-specific info (can be null)
     * @param outLen desired output length (max 255 * 32 = 8160 bytes)
     * @return derived key material
     */
    public static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int outLen) {
        byte[] prk = extract(salt, ikm);
        return expand(prk, info, outLen);
    }

    /**
     * HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
     * If salt is null/empty, uses a string of zeros of length HashLen.
     */
    public static byte[] extract(byte[] salt, byte[] ikm) {
        if (ikm == null) {
            throw new IllegalArgumentException("IKM cannot be null");
        }
        if (salt == null || salt.length == 0) {
            salt = new byte[32]; // Zero-filled salt
        }
        return hmacSha256(salt, ikm);
    }

    /**
     * HKDF-Expand: OKM = HMAC-Hash(PRK, info || counter) ...
     * @param prk pseudo-random key (output of extract)
     * @param info optional context/application-specific info
     * @param outLen desired output length
     * @return derived key material
     */
    public static byte[] expand(byte[] prk, byte[] info, int outLen) {
        if (prk == null || prk.length != 32) {
            throw new IllegalArgumentException("PRK must be 32 bytes");
        }
        if (outLen > 8160) { // 255 * 32
            throw new IllegalArgumentException("Output length too large");
        }

        int n = (outLen + HASH_LEN - 1) / HASH_LEN;
        byte[] okm = new byte[outLen];
        byte[] t = new byte[0];
        int okmPos = 0;
        
        for (int i = 1; i <= n; i++) {
            // T(i) = HMAC-Hash(PRK, T(i-1) || info || 0x??)
            byte[] data = new byte[t.length + (info != null ? info.length : 0) + 1];
            System.arraycopy(t, 0, data, 0, t.length);
            if (info != null) {
                System.arraycopy(info, 0, data, t.length, info.length);
            }
            data[data.length - 1] = (byte) i;
            
            t = hmacSha256(prk, data);
            
            int toCopy = Math.min(HASH_LEN, outLen - okmPos);
            System.arraycopy(t, 0, okm, okmPos, toCopy);
            okmPos += toCopy;
        }
        
        return okm;
    }

    /**
     * HMAC-SHA256 keyed hash.
     * @param key HMAC key
     * @param data data to hash
     * @return 32-byte HMAC-SHA256 digest
     */
    public static byte[] hmacSha256(byte[] key, byte[] data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("Key and data cannot be null");
        }
        
        // ipad = 0x36 repeated, opad = 0x5C repeated
        // Block size for SHA-256 is 64 bytes
        byte[] k = key;
        if (k.length > 64) {
            // Hash key if longer than block size
            k = SHA256.hash(key);
        }
        
        // ipad
        byte[] ipad = new byte[64];
        for (int i = 0; i < 64; i++) {
            ipad[i] = (byte) 0x36;
        }
        for (int i = 0; i < k.length; i++) {
            ipad[i] ^= k[i];
        }
        
        // opad
        byte[] opad = new byte[64];
        for (int i = 0; i < 64; i++) {
            opad[i] = (byte) 0x5C;
        }
        for (int i = 0; i < k.length; i++) {
            opad[i] ^= k[i];
        }
        
        // HMAC = Hash(opad || Hash(ipad || data))
        byte[] inner = new byte[64 + data.length];
        System.arraycopy(ipad, 0, inner, 0, 64);
        System.arraycopy(data, 0, inner, 64, data.length);
        byte[] innerHash = SHA256.hash(inner);
        
        byte[] outer = new byte[64 + 32];
        System.arraycopy(opad, 0, outer, 0, 64);
        System.arraycopy(innerHash, 0, outer, 64, 32);
        
        return SHA256.hash(outer);
    }
}