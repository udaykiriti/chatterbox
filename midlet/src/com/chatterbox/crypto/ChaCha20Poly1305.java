package com.chatterbox.crypto;

/**
 * ChaCha20-Poly1305 AEAD (Authenticated Encryption with Associated Data).
 * 
 * Reference: RFC 8439
 */
public final class ChaCha20Poly1305 {

    private ChaCha20Poly1305() {}

    public static final int KEY_SIZE = 32;
    public static final int NONCE_SIZE = 12;
    public static final int TAG_SIZE = 16;

    /**
     * Encrypt plaintext with associated data.
     * @param key 32-byte key
     * @param nonce 12-byte nonce (must be unique per key!)
     * @param aad associated authenticated data (can be empty)
     * @param plaintext plaintext to encrypt
     * @param out output buffer (must be at least plaintext.length + 16)
     * @return number of bytes written (ciphertext + 16-byte tag)
     */
    public static int encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext, byte[] out) {
        validateInputs(key, nonce);
        
        int ptLen = plaintext.length;
        if (out.length < ptLen + 16) {
            throw new IllegalArgumentException("Output buffer too small");
        }

        // Derive Poly1305 key from ChaCha20 block 0
        byte[] polyKey = new byte[32];
        derivePolyKey(key, nonce, polyKey);

        // Encrypt plaintext starting at counter = 1
        ChaCha20.crypt(key, nonce, 1, plaintext, 0, plaintext.length, out, 0);

        // Compute tag
        byte[] tag = new byte[16];
        computeTag(polyKey, aad, out, 0, plaintext.length, tag);

        // Append tag to output
        System.arraycopy(tag, 0, out, plaintext.length, 16);

        return plaintext.length + 16;
    }

    /**
     * Decrypt ciphertext with associated data.
     * @return number of plaintext bytes written, or -1 if authentication fails
     */
    public static int decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertextWithTag, int offset, int length, byte[] out) {
        validateInputs(key, nonce);
        
        if (ciphertextWithTag == null || length < 16) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        int ctLen = length - 16;
        int tagOffset = offset + ctLen;

        // Derive Poly1305 key
        byte[] polyKey = new byte[32];
        derivePolyKey(key, nonce, polyKey);

        // Compute expected tag
        byte[] computedTag = new byte[16];
        computeTag(polyKey, aad, ciphertextWithTag, offset, ctLen, computedTag);
        
        // Extract provided tag
        byte[] providedTag = new byte[16];
        System.arraycopy(ciphertextWithTag, tagOffset, providedTag, 0, 16);

        // Constant-time tag comparison
        if (!constantTimeEquals(computedTag, 0, providedTag, 0, 16)) {
            return -1;
        }

        // Decrypt plaintext
        if (out.length < ctLen) {
            throw new IllegalArgumentException("Output buffer too small");
        }
        ChaCha20.crypt(key, nonce, 1, ciphertextWithTag, offset, ctLen, out, 0);

        return ctLen;
    }

    /**
     * Decrypt with separate ciphertext and tag.
     */
    public static int decrypt(byte[] key, byte[] nonce, byte[] aad, 
                              byte[] ciphertext, int ctOffset, int ctLen,
                              byte[] tag, int tagOffset,
                              byte[] out) {
        byte[] polyKey = new byte[32];
        derivePolyKey(key, nonce, polyKey);
        
        byte[] tagData = buildTagData(aad, ciphertext, ctOffset, ctLen);
        byte[] computedTag = new byte[16];
        Poly1305.mac(polyKey, tagData, computedTag);
        
        if (!constantTimeEquals(computedTag, 0, tag, tagOffset, 16)) {
            return -1;
        }

        if (out.length < ctLen) {
            throw new IllegalArgumentException("Output buffer too small");
        }
        ChaCha20.crypt(key, nonce, 1, ciphertext, ctOffset, ctLen, out, 0);
        return ctLen;
    }

    // ============================================================
    // Internal
    // ============================================================

    private static void derivePolyKey(byte[] key, byte[] nonce, byte[] out) {
        int[] state = new int[16];
        ChaCha20.initStateForPoly(key, nonce, state);
        byte[] block0 = new byte[64];
        ChaCha20.generateBlockForPoly(state, block0);
        System.arraycopy(block0, 0, out, 0, 32);
    }

    private static void computeTag(byte[] polyKey, byte[] aad, byte[] ciphertext, int ctOffset, int ctLen, byte[] tag) {
        int aadLen = aad != null ? aad.length : 0;
        int totalLen = padLen(aadLen) + padLen(ctLen) + 16;
        byte[] data = new byte[totalLen];
        int pos = 0;
        
        if (aadLen > 0) {
            System.arraycopy(aad, 0, data, 0, aadLen);
        }
        pos = padLen(aadLen);
        
        if (ctLen > 0) {
            System.arraycopy(ciphertext, ctOffset, data, pos, ctLen);
        }
        pos += ctLen;
        pos = padLen(pos);
        
        // Lengths (8 bytes each, little-endian)
        write64(data, pos, aadLen);
        write64(data, pos + 8, ctLen);
        
        Poly1305.mac(polyKey, data, tag);
    }

    private static byte[] buildTagData(byte[] aad, byte[] ciphertext, int ctOffset, int ctLen) {
        int aadLen = aad != null ? aad.length : 0;
        int totalLen = padLen(aadLen) + padLen(ctLen) + 16;
        byte[] data = new byte[totalLen];
        int pos = 0;
        
        if (aadLen > 0) {
            System.arraycopy(aad, 0, data, 0, aadLen);
        }
        pos = padLen(aadLen);
        
        System.arraycopy(ciphertext, ctOffset, data, pos, ctLen);
        pos += ctLen;
        pos = padLen(pos);
        
        write64(data, pos, aadLen);
        write64(data, pos + 8, ctLen);
        
        return data;
    }

    private static int padLen(int len) {
        int rem = len % 16;
        return rem == 0 ? len : len + (16 - rem);
    }

    private static void write64(byte[] buf, int off, long val) {
        buf[off] = (byte) (val & 0xFF);
        buf[off+1] = (byte) ((val >> 8) & 0xFF);
        buf[off+2] = (byte) ((val >> 16) & 0xFF);
        buf[off+3] = (byte) ((val >> 24) & 0xFF);
        buf[off+4] = (byte) ((val >> 32) & 0xFF);
        buf[off+5] = (byte) ((val >> 40) & 0xFF);
        buf[off+6] = (byte) ((val >> 48) & 0xFF);
        buf[off+7] = (byte) ((val >> 56) & 0xFF);
    }

    private static boolean constantTimeEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        int diff = 0;
        for (int i = 0; i < len; i++) {
            diff |= a[aOff + i] ^ b[bOff + i];
        }
        return diff == 0;
    }

    private static void validateInputs(byte[] key, byte[] nonce) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("Key must be 32 bytes");
        }
        if (nonce == null || nonce.length != 12) {
            throw new IllegalArgumentException("Nonce must be 12 bytes");
        }
    }
}