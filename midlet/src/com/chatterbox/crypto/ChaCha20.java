package com.chatterbox.crypto;

/**
 * ChaCha20 stream cipher implementation for CLDC 1.1.
 * 
 * Reference: RFC 8439, "ChaCha20 and Poly1305 for IETF Protocols"
 */
public final class ChaCha20 {

    private ChaCha20() {}

    public static final int KEY_SIZE = 32;
    public static final int NONCE_SIZE = 12;
    public static final int BLOCK_SIZE = 64;

    /**
     * Encrypt/decrypt data using ChaCha20 (same operation).
     * @param key 32-byte key
     * @param nonce 12-byte nonce
     * @param counter initial counter value
     * @param data data to encrypt/decrypt
     * @param offset offset in data
     * @param length number of bytes to process
     * @param out output buffer
     * @param outOffset offset in output buffer
     */
    public static void crypt(byte[] key, byte[] nonce, int counter, 
                             byte[] data, int offset, int length, 
                             byte[] out, int outOffset) {
        if (key == null || key.length != KEY_SIZE) {
            throw new IllegalArgumentException("Key must be 32 bytes");
        }
        if (nonce == null || nonce.length != NONCE_SIZE) {
            throw new IllegalArgumentException("Nonce must be 12 bytes");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (out == null || out.length < outOffset + length) {
            throw new IllegalArgumentException("Output buffer too small");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }

        int[] state = new int[16];
        initState(key, nonce, counter, state);

        byte[] keystream = new byte[BLOCK_SIZE];
        int pos = 0;

        while (pos < length) {
            generateBlock(state, keystream);

            int remaining = length - pos;
            int chunk = remaining < BLOCK_SIZE ? remaining : BLOCK_SIZE;
            for (int i = 0; i < chunk; i++) {
                out[outOffset + pos + i] = (byte) (data[offset + pos + i] ^ keystream[i]);
            }
            pos += chunk;

            incrementCounter(state);
        }
    }

    /** Convenience: encrypt/decrypt from start with no offsets. */
    public static void crypt(byte[] key, byte[] nonce, int counter, 
                             byte[] data, byte[] out) {
        crypt(key, nonce, counter, data, 0, data.length, out, 0);
    }

    /** Generate keystream only. */
    public static void keystream(byte[] key, byte[] nonce, int counter, byte[] out) {
        int[] state = new int[16];
        initState(key, nonce, counter, state);

        byte[] keystream = new byte[BLOCK_SIZE];
        for (int i = 0; i < out.length; i += BLOCK_SIZE) {
            generateBlock(state, keystream);
            int len = Math.min(BLOCK_SIZE, out.length - i);
            System.arraycopy(keystream, 0, out, i, len);
            incrementCounter(state);
        }
    }

    // For Poly1305 key derivation (ChaCha20 block 0 with counter = 0)
    public static void initStateForPoly(byte[] key, byte[] nonce, int[] state) {
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;

        for (int i = 0; i < 8; i++) {
            state[4 + i] = load32(key, i * 4);
        }

        state[12] = 0;
        state[13] = load32(nonce, 0);
        state[14] = load32(nonce, 4);
        state[15] = load32(nonce, 8);
    }

    public static void generateBlockForPoly(int[] state, byte[] out) {
        int[] x = new int[16];
        System.arraycopy(state, 0, x, 0, 16);

        for (int i = 0; i < 10; i++) {
            quarterRound(x, 0, 4, 8, 12);
            quarterRound(x, 1, 5, 9, 13);
            quarterRound(x, 2, 6, 10, 14);
            quarterRound(x, 3, 7, 11, 15);
            quarterRound(x, 0, 5, 10, 15);
            quarterRound(x, 1, 6, 11, 12);
            quarterRound(x, 2, 7, 8, 13);
            quarterRound(x, 3, 4, 9, 14);
        }

        for (int i = 0; i < 16; i++) {
            x[i] = add32(x[i], state[i]);
        }

        for (int i = 0; i < 16; i++) {
            int v = x[i];
            out[4*i] = (byte) (v & 0xFF);
            out[4*i + 1] = (byte) ((v >> 8) & 0xFF);
            out[4*i + 2] = (byte) ((v >> 16) & 0xFF);
            out[4*i + 3] = (byte) ((v >> 24) & 0xFF);
        }
    }

    // ============================================================
    // Internal
    // ============================================================

    private static void initState(byte[] key, byte[] nonce, int counter, int[] state) {
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;

        for (int i = 0; i < 8; i++) {
            state[4 + i] = load32(key, i * 4);
        }

        state[12] = counter;
        state[13] = load32(nonce, 0);
        state[14] = load32(nonce, 4);
        state[15] = load32(nonce, 8);
    }

    private static void generateBlock(int[] state, byte[] out) {
        int[] x = new int[16];
        System.arraycopy(state, 0, x, 0, 16);

        for (int i = 0; i < 10; i++) {
            quarterRound(x, 0, 4, 8, 12);
            quarterRound(x, 1, 5, 9, 13);
            quarterRound(x, 2, 6, 10, 14);
            quarterRound(x, 3, 7, 11, 15);
            quarterRound(x, 0, 5, 10, 15);
            quarterRound(x, 1, 6, 11, 12);
            quarterRound(x, 2, 7, 8, 13);
            quarterRound(x, 3, 4, 9, 14);
        }

        for (int i = 0; i < 16; i++) {
            x[i] = add32(x[i], state[i]);
        }

        for (int i = 0; i < 16; i++) {
            int v = x[i];
            out[4*i] = (byte) (v & 0xFF);
            out[4*i + 1] = (byte) ((v >> 8) & 0xFF);
            out[4*i + 2] = (byte) ((v >> 16) & 0xFF);
            out[4*i + 3] = (byte) ((v >> 24) & 0xFF);
        }
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] = add32(x[a], x[b]); x[d] = rotl32(x[d] ^ x[a], 16);
        x[c] = add32(x[c], x[d]); x[b] = rotl32(x[b] ^ x[c], 12);
        x[a] = add32(x[a], x[b]); x[d] = rotl32(x[d] ^ x[a], 8);
        x[c] = add32(x[c], x[d]); x[b] = rotl32(x[b] ^ x[c], 7);
    }

    private static void incrementCounter(int[] state) {
        int c = state[12] + 1;
        state[12] = c;
        if (c == 0) {
            state[13]++;
        }
    }

    private static int load32(byte[] buf, int off) {
        return (buf[off] & 0xFF) |
               ((buf[off+1] & 0xFF) << 8) |
               ((buf[off+2] & 0xFF) << 16) |
               ((buf[off+3] & 0xFF) << 24);
    }

    private static int add32(int a, int b) {
        return (a + b) & 0xFFFFFFFF;
    }

    private static int rotl32(int x, int n) {
        return (x << n) | (x >>> (32 - n));
    }
}