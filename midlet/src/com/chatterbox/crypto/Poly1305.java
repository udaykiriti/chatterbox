package com.chatterbox.crypto;

/**
 * Poly1305 message authentication code for CLDC 1.1.
 *
 * Reference: RFC 8439 section 2.5.
 *
 * Implementation follows the well-known 32-bit limbed design (donna/Moon),
 * keeping every limb below 2^26 so all products fit comfortably in 64-bit
 * longs and the "5x" cross-multipliers stay exact.
 *
 * Correctness is pinned by RFC 8439 vectors in CryptoTest:
 *  - section 2.5.2 (34-byte message -> tag a8:06:1d:c1:...)
 *  - section A.3 #1 (all-zero key/message)
 * and indirectly by ChaCha20Poly1305 AEAD vectors in 2.8.2.
 */
public final class Poly1305 {

    private Poly1305() {}

    public static final int KEY_SIZE = 32;
    public static final int TAG_SIZE = 16;

    /** 26-bit mask, the limb ceiling. */
    private static final int MASK26 = 0x3FFFFFF;

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

        // r = clamped first 16 bytes, as five 26-bit little-endian limbs.
        int r0 = (load32(key, 0)) & 0x3FFFFFF;
        int r1 = (load32(key, 3) >>> 2) & 0x3FFFFFF;
        int r2 = (load32(key, 6) >>> 4) & 0x3FFFFFF;
        int r3 = (load32(key, 9) >>> 6) & 0x3FFFFFF;
        int r4 = (load32(key, 12) >>> 8) & 0x3FFFFFF;
        r0 &= 0x3ffffff;
        r1 &= 0x3ffff03;
        r2 &= 0x3ffc0ff;
        r3 &= 0x3f03fff;
        r4 &= 0x000fffff;

        // s[i] = 5 * r[i] for the cross terms (bits >= 2^130 equal *5 mod p).
        int s1 = r1 * 5;
        int s2 = r2 * 5;
        int s3 = r3 * 5;
        int s4 = r4 * 5;

        long h0 = 0, h1 = 0, h2 = 0, h3 = 0, h4 = 0;
        int len = msg.length;
        int i = 0;

        while (i + 16 <= len) {
            h0 += load32(msg, i) & 0x3FFFFFF;
            h1 += (load32(msg, i + 3) >>> 2) & 0x3FFFFFF;
            h2 += (load32(msg, i + 6) >>> 4) & 0x3FFFFFF;
            h3 += (load32(msg, i + 9) >>> 6) & 0x3FFFFFF;
            h4 += ((load32(msg, i + 12) >>> 8) & 0xFFFFFF) | (1L << 24);
            long[] d = mulAdd(h0, h1, h2, h3, h4, r0, r1, r2, r3, r4, s1, s2, s3, s4);
            h0 = d[0]; h1 = d[1]; h2 = d[2]; h3 = d[3]; h4 = d[4];
            i += 16;
        }

        if (i < len) {
            // Final partial block: copy the remainder, zero-pad, and place the
            // 0x01 tail bit at byte index (len - i) — it is already inside the
            // loaded window below, so no extra bit is added here.
            byte[] tail = new byte[16];
            System.arraycopy(msg, i, tail, 0, len - i);
            tail[len - i] = 1;
            h0 += load32(tail, 0) & 0x3FFFFFF;
            h1 += (load32(tail, 3) >>> 2) & 0x3FFFFFF;
            h2 += (load32(tail, 6) >>> 4) & 0x3FFFFFF;
            h3 += (load32(tail, 9) >>> 6) & 0x3FFFFFF;
            h4 += (load32(tail, 12) >>> 8) & 0xFFFFFF;
            long[] d = mulAdd(h0, h1, h2, h3, h4, r0, r1, r2, r3, r4, s1, s2, s3, s4);
            h0 = d[0]; h1 = d[1]; h2 = d[2]; h3 = d[3]; h4 = d[4];
        }

        // ---- Fully carry. ----
        long c;
        c = h1 >> 26; h1 &= MASK26; h2 += c;
        c = h2 >> 26; h2 &= MASK26; h3 += c;
        c = h3 >> 26; h3 &= MASK26; h4 += c;
        c = h4 >> 26; h4 &= MASK26; h0 += c * 5;
        c = h0 >> 26; h0 &= MASK26; h1 += c;

        // ---- Reduce: if h >= p, subtract p = 2^130 - 5. ----
        long g0 = h0 + 5;
        c = g0 >> 26; g0 &= MASK26;
        long g1 = h1 + c;
        c = g1 >> 26; g1 &= MASK26;
        long g2 = h2 + c;
        c = g2 >> 26; g2 &= MASK26;
        long g3 = h3 + c;
        c = g3 >> 26; g3 &= MASK26;
        long g4 = h4 + c - (1L << 26);

        // Select g (= h - p) only when h >= p (branchless donna-style select).
        long sel = ~(g4 >> 63);                     // -1 iff h >= p
        h0 = ((h0 & ~sel) | (g0 & sel));
        h1 = ((h1 & ~sel) | (g1 & sel));
        h2 = ((h2 & ~sel) | (g2 & sel));
        h3 = ((h3 & ~sel) | (g3 & sel));
        h4 = ((h4 & ~sel) | (g4 & sel));

        // ---- Serialize little-endian (each word truncated to 32 bits), then
        // add s (mod 2^128). Carries propagate between words; only the carry
        // out of word 3 is dropped. ----
        long v = (h0 | (h1 << 26)) & 0xFFFFFFFFL;
        v += load32(key, 16) & 0xFFFFFFFFL;
        long carry = v >> 32;
        v &= 0xFFFFFFFFL;
        store32(out, 0, v);

        v = (((h1 >>> 6) | (h2 << 20)) & 0xFFFFFFFFL) + (load32(key, 20) & 0xFFFFFFFFL) + carry;
        carry = v >> 32;
        v &= 0xFFFFFFFFL;
        store32(out, 4, v);

        v = (((h2 >>> 12) | (h3 << 14)) & 0xFFFFFFFFL) + (load32(key, 24) & 0xFFFFFFFFL) + carry;
        carry = v >> 32;
        v &= 0xFFFFFFFFL;
        store32(out, 8, v);

        v = (((h3 >>> 18) | (h4 << 8)) & 0xFFFFFFFFL) + (load32(key, 28) & 0xFFFFFFFFL) + carry;
        store32(out, 12, v & 0xFFFFFFFFL);   // carry out of the top word is lost: mod 2^128
    }

    /**
     * (h0..h4) * (r0..r4) mod 2^130-5, all limbs < 2^26 in, < 2^26+carry out.
     */
    private static long[] mulAdd(long h0, long h1, long h2, long h3, long h4,
                                 int r0, int r1, int r2, int r3, int r4,
                                 int s1, int s2, int s3, int s4) {
        long d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1;
        long d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2;
        long d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3;
        long d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4;
        long d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0;

        long c = d0 >> 26; d0 &= MASK26;
        d1 += c;
        c = d1 >> 26; d1 &= MASK26;
        d2 += c;
        c = d2 >> 26; d2 &= MASK26;
        d3 += c;
        c = d3 >> 26; d3 &= MASK26;
        d4 += c;
        c = d4 >> 26; d4 &= MASK26;
        d0 += c * 5;
        c = d0 >> 26; d0 &= MASK26;
        d1 += c;
        return new long[] { d0, d1, d2, d3, d4 };
    }

    /** The tag is (acc + s) mod 2^128; s is added at serialization in mac(). */
    public static boolean verify(byte[] key, byte[] msg, byte[] tag) {
        if (tag == null || tag.length != TAG_SIZE) {
            return false;
        }
        byte[] expected = new byte[TAG_SIZE];
        mac(key, msg, expected);
        return constantTimeEquals(tag, expected);
    }

    private static int load32(byte[] buf, int off) {
        return (buf[off] & 0xFF) |
               ((buf[off + 1] & 0xFF) << 8) |
               ((buf[off + 2] & 0xFF) << 16) |
               ((buf[off + 3] & 0xFF) << 24);
    }

    private static void store32(byte[] buf, int off, long v) {
        buf[off] = (byte) (v & 0xFF);
        buf[off + 1] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 3] = (byte) ((v >>> 24) & 0xFF);
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
