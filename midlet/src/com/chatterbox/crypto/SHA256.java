package com.chatterbox.crypto;

/**
 * SHA-256 hash function implementation for CLDC 1.1.
 * 
 * Reference: FIPS 180-4
 * 
 * This is a minimal implementation optimized for CLDC 1.1.
 * Uses 32-bit integer arithmetic throughout.
 */
public final class SHA256 {

    private SHA256() {}

    public static final int DIGEST_LENGTH = 32; // 256 bits
    public static final int BLOCK_SIZE = 64;    // 512 bits

    /**
     * Compute SHA-256 hash of data.
     * @param data input data
     * @return 32-byte hash
     */
    public static byte[] hash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        // Initialize hash values (first 32 bits of fractional parts of square roots of first 8 primes)
        int[] h = new int[] {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        };

        // Pre-processing: padding
        int dataLen = data.length;
        long bitLen = (long) dataLen * 8;

        // Pad: append 0x80, then zeros, then 64-bit length (big-endian)
        int paddedLen = dataLen + 1 + 8;
        int remainder = paddedLen % BLOCK_SIZE;
        if (remainder != 0) {
            paddedLen += BLOCK_SIZE - remainder;
        }

        byte[] padded = new byte[paddedLen];
        System.arraycopy(data, 0, padded, 0, dataLen);
        padded[dataLen] = (byte) 0x80;
        // Zeros already filled
        
        // Append length as 64-bit big-endian
        for (int i = 0; i < 8; i++) {
            padded[paddedLen - 8 + i] = (byte) ((bitLen >> (56 - i * 8)) & 0xFF);
        }

        // Process each 64-byte block
        int[] w = new int[64];
        int[] a = new int[8];
        
        for (int blockOff = 0; blockOff < paddedLen; blockOff += BLOCK_SIZE) {
            // Prepare message schedule
            for (int i = 0; i < 16; i++) {
                int off = blockOff + i * 4;
                w[i] = ((padded[off] & 0xFF) << 24) |
                       ((padded[off + 1] & 0xFF) << 16) |
                       ((padded[off + 2] & 0xFF) << 8) |
                       (padded[off + 3] & 0xFF);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = rotr(w[i-15], 7) ^ rotr(w[i-15], 18) ^ (w[i-15] >>> 3);
                int s1 = rotr(w[i-2], 17) ^ rotr(w[i-2], 19) ^ (w[i-2] >>> 10);
                w[i] = w[i-16] + s0 + w[i-7] + s1;
            }

            // Initialize working variables
            System.arraycopy(h, 0, a, 0, 8);

            // Main loop
            for (int i = 0; i < 64; i++) {
                int s1 = rotr(a[4], 6) ^ rotr(a[4], 11) ^ rotr(a[4], 25);
                int ch = (a[4] & a[5]) ^ (~a[4] & a[6]);
                int temp1 = a[7] + s1 + ch + K[i] + w[i];
                int s0 = rotr(a[0], 2) ^ rotr(a[0], 13) ^ rotr(a[0], 22);
                int maj = (a[0] & a[1]) ^ (a[0] & a[2]) ^ (a[1] & a[2]);
                int temp2 = s0 + maj;

                a[7] = a[6];
                a[6] = a[5];
                a[5] = a[4];
                a[4] = a[3] + temp1;
                a[3] = a[2];
                a[2] = a[1];
                a[1] = a[0];
                a[0] = temp1 + temp2;
            }

            // Add compressed chunk to current hash value
            for (int i = 0; i < 8; i++) {
                h[i] += a[i];
            }
        }

        // Produce final hash (big-endian)
        byte[] digest = new byte[32];
        for (int i = 0; i < 8; i++) {
            int v = h[i];
            digest[i*4] = (byte) ((v >> 24) & 0xFF);
            digest[i*4+1] = (byte) ((v >> 16) & 0xFF);
            digest[i*4+2] = (byte) ((v >> 8) & 0xFF);
            digest[i*4+3] = (byte) (v & 0xFF);
        }
        return digest;
    }

    private static int rotr(int x, int n) {
        return (x >>> n) | (x << (32 - n));
    }

    // SHA-256 constants (first 32 bits of fractional parts of cube roots of first 64 primes)
    private static final int[] K = new int[] {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
}