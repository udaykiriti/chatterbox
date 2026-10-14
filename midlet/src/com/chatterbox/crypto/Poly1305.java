package com.chatterbox.crypto;

/**
 * Poly1305 message authentication code for CLDC 1.1.
 * 
 * Reference: RFC 8439
 */
public final class Poly1305 {

    private Poly1305() {}

    public static final int KEY_SIZE = 32;
    public static final int TAG_SIZE = 16;

    /**
     * Compute Poly1305 MAC.
     */
    public static void mac(byte[] key, byte[] msg, byte[] out) {
        if (key == null || key.length != KEY_SIZE) {
            throw new IllegalArgumentException("Key must be 32 bytes");
        }
        if (msg == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (out == null || out.length != TAG_SIZE) {
            throw new IllegalArgumentException("Output must be 16 bytes");
        }

        // Split key: r (16 bytes, clamped) | s (16 bytes)
        int[] r = new int[10]; // Base 2^26: 5 limbs of 26 bits
        int[] s = new int[4];

        // Load r (16 bytes, little-endian)
        for (int i = 0; i < 16; i++) {
            int limb = i >> 1;
            int shift = (i & 1) * 8;
            if (limb < 5) {
                r[limb] |= ((key[i] & 0xFF) << shift);
            }
        }
        // Clamp r
        r[0] &= 0x3FFFFFF; // 26 bits
        r[1] &= 0x3FFFF03;
        r[2] &= 0x3FFC0FF;
        r[3] &= 0x3F03FFF;
        r[4] &= 0x0F3FFFF;

        // Load s (16 bytes, little-endian)
        for (int i = 0; i < 4; i++) {
            s[i] = load32(key, 16 + i * 4);
        }

        // Accumulator (base 2^26)
        int[] acc = new int[5];

        // Process message in 16-byte blocks
        int offset = 0;
        int len = msg.length;
        while (len > 0) {
            int blockLen = len < 16 ? len : 16;
            int[] block = new int[5];
            
            // Load block (little-endian, base 2^8)
            for (int i = 0; i < blockLen; i++) {
                int limb = i >> 1;
                int shift = (i & 1) * 8;
                if (limb < 5) {
                    block[limb] |= ((msg[offset + i] & 0xFF) << shift);
                }
            }
            // Add padding bit (1 at position blockLen)
            int padLimb = blockLen >> 1;
            int padShift = (blockLen & 1) * 8;
            if (padLimb < 5) {
                block[padLimb] |= (1 << padShift);
            }
            
            // Add block to accumulator
            for (int i = 0; i < 5; i++) {
                acc[i] += block[i];
            }

            // Multiply by r modulo 2^130 - 5
            multiplyMod(acc, r);

            offset += blockLen;
            len -= blockLen;
        }

        // Add s
        add130(acc, s);

        // Serialize tag (little-endian)
        for (int i = 0; i < 4; i++) {
            int v = acc[i];
            out[4*i] = (byte) (v & 0xFF);
            out[4*i + 1] = (byte) ((v >> 8) & 0xFF);
            out[4*i + 2] = (byte) ((v >> 16) & 0xFF);
            out[4*i + 3] = (byte) ((v >> 24) & 0xFF);
        }
    }

    public static boolean verify(byte[] key, byte[] msg, byte[] tag) {
        if (tag == null || tag.length != TAG_SIZE) {
            return false;
        }
        byte[] expected = new byte[TAG_SIZE];
        mac(key, msg, expected);
        return constantTimeEquals(tag, expected);
    }

    private static void multiplyMod(int[] acc, int[] r) {
        long[] t = new long[9];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                t[i+j] += (long)(acc[i] & 0x3FFFFFF) * r[j];
            }
        }

        // Reduce modulo 2^130 - 5
        t[0] += t[8] * 5;
        t[1] += (t[0] >> 26); t[0] &= 0x3FFFFFF;
        t[2] += (t[1] >> 26); t[1] &= 0x3FFFFFF;
        t[3] += (t[2] >> 26); t[2] &= 0x3FFFFFF;
        t[4] += (t[3] >> 26); t[3] &= 0x3FFFFFF;
        t[0] += (t[4] >> 26) * 5;
        t[4] &= 0x3FFFFFF;

        // Final carry
        for (int i = 0; i < 4; i++) {
            t[i+1] += (t[i] >> 26);
            t[i] &= 0x3FFFFFF;
        }
        t[4] &= 0x3FFFFFF;
        t[0] += (t[4] >> 26) * 5;
        t[4] &= 0x3FFFFFF;
        t[1] += (t[0] >> 26); t[0] &= 0x3FFFFFF;
        t[2] += (t[1] >> 26); t[1] &= 0x3FFFFFF;
        t[3] += (t[2] >> 26); t[2] &= 0x3FFFFFF;
        t[4] += (t[3] >> 26); t[3] &= 0x3FFFFFF;

        for (int i = 0; i < 5; i++) {
            acc[i] = (int) t[i];
        }
    }

    private static void add130(int[] acc, int[] s) {
        long t0 = (acc[0] & 0x3FFFFFFL) + (s[0] & 0xFFFFFFFFL);
        long t1 = (acc[1] & 0x3FFFFFFL) + (s[1] & 0xFFFFFFFFL) + (t0 >> 26);
        long t2 = (acc[2] & 0x3FFFFFFL) + (s[2] & 0xFFFFFFFFL) + (t1 >> 26);
        long t3 = (acc[3] & 0x3FFFFFFL) + (s[3] & 0xFFFFFFFFL) + (t2 >> 26);
        long t4 = (acc[4] & 0x3FFFFFFL) + (t3 >> 26);

        acc[0] = (int)(t0 & 0x3FFFFFF);
        acc[1] = (int)(t1 & 0x3FFFFFF);
        acc[2] = (int)(t2 & 0x3FFFFFF);
        acc[3] = (int)(t3 & 0x3FFFFFF);
        acc[4] = (int)(t4 & 0x3FFFFFF);
    }

    private static int load32(byte[] buf, int off) {
        return (buf[off] & 0xFF) |
               ((buf[off+1] & 0xFF) << 8) |
               ((buf[off+2] & 0xFF) << 16) |
               ((buf[off+3] & 0xFF) << 24);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}