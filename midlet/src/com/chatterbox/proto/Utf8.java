package com.chatterbox.proto;

import java.io.ByteArrayOutputStream;

/**
 * Minimal UTF-8 codec. CLDC 1.1 has no java.nio.charset, so we roll our own.
 *
 * Behaviour notes / assumptions:
 *  - Encoding always emits well-formed UTF-8, no BOM.
 *  - Decoding is lenient: any malformed or truncated sequence becomes U+FFFD and
 *    the decoder resynchronises. A chat client must never throw on bad bytes
 *    coming off a hostile or misconfigured server.
 *  - ByteArrayOutputStream in CLDC 1.1 does NOT declare IOException on write(),
 *    so there is no try/catch here. Compiling against CLDC 1.0 would fail.
 */
public final class Utf8 {

    /** Replacement character for undecodable input. */
    public static final char REPLACEMENT = '�';

    private Utf8() {
    }

    public static byte[] encode(String s) {
        if (s == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() + 8);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int c = s.charAt(i);
            // Fold a valid surrogate pair into one code point.
            if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len) {
                int lo = s.charAt(i + 1);
                if (lo >= 0xDC00 && lo <= 0xDFFF) {
                    c = 0x10000 + ((c - 0xD800) << 10) + (lo - 0xDC00);
                    i++;
                }
            }
            if (c < 0x80) {
                out.write(c);
            } else if (c < 0x800) {
                out.write(0xC0 | (c >> 6));
                out.write(0x80 | (c & 0x3F));
            } else if (c < 0x10000) {
                out.write(0xE0 | (c >> 12));
                out.write(0x80 | ((c >> 6) & 0x3F));
                out.write(0x80 | (c & 0x3F));
            } else {
                out.write(0xF0 | (c >> 18));
                out.write(0x80 | ((c >> 12) & 0x3F));
                out.write(0x80 | ((c >> 6) & 0x3F));
                out.write(0x80 | (c & 0x3F));
            }
        }
        return out.toByteArray();
    }

    public static byte[] encode(String s, ByteArrayOutputStream reuse) {
        if (reuse == null) {
            return encode(s);
        }
        reuse.reset();
        byte[] b = encode(s);
        reuse.write(b, 0, b.length);
        return b;
    }

    public static String decode(byte[] b) {
        return decode(b, 0, b.length);
    }

    public static String decode(byte[] b, int off, int len) {
        if (b == null || len <= 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer(len);
        int i = off;
        int end = off + len;
        while (i < end) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                sb.append((char) c);
                i++;
            } else if ((c & 0xE0) == 0xC0) {
                if (i + 1 >= end) {
                    sb.append(REPLACEMENT);
                    break;
                }
                int c1 = b[i + 1] & 0xFF;
                if ((c1 & 0xC0) != 0x80) {
                    sb.append(REPLACEMENT);
                    i++;
                    continue;
                }
                sb.append((char) (((c & 0x1F) << 6) | (c1 & 0x3F)));
                i += 2;
            } else if ((c & 0xF0) == 0xE0) {
                if (i + 2 >= end) {
                    sb.append(REPLACEMENT);
                    break;
                }
                int c1 = b[i + 1] & 0xFF;
                int c2 = b[i + 2] & 0xFF;
                if ((c1 & 0xC0) != 0x80 || (c2 & 0xC0) != 0x80) {
                    sb.append(REPLACEMENT);
                    i++;
                    continue;
                }
                sb.append((char) (((c & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F)));
                i += 3;
            } else if ((c & 0xF8) == 0xF0) {
                if (i + 3 >= end) {
                    sb.append(REPLACEMENT);
                    break;
                }
                int c1 = b[i + 1] & 0xFF;
                int c2 = b[i + 2] & 0xFF;
                int c3 = b[i + 3] & 0xFF;
                if ((c1 & 0xC0) != 0x80 || (c2 & 0xC0) != 0x80 || (c3 & 0xC0) != 0x80) {
                    sb.append(REPLACEMENT);
                    i++;
                    continue;
                }
                // Surrogate pair. CLDC Strings are UTF-16 so this is representable.
                int cp = ((c & 0x07) << 18) | ((c1 & 0x3F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                if (cp >= 0x10000) {
                    cp -= 0x10000;
                    sb.append((char) (0xD800 + (cp >> 10)));
                    sb.append((char) (0xDC00 + (cp & 0x3FF)));
                } else {
                    sb.append((char) cp);
                }
                i += 4;
            } else {
                sb.append(REPLACEMENT);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * True if the bytes are plausibly valid UTF-8 containing a multi-byte
     * sequence. Used by LineReader to decide whether to hand a line to the
     * UTF-8 decoder or fall back to ISO-8859-1 (latin1) for legacy servers.
     */
    public static boolean looksUtf8(byte[] b, int off, int len) {
        int i = off;
        int end = off + len;
        while (i < end) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                i++;
            } else if ((c & 0xE0) == 0xC0 && i + 1 < end) {
                i += 2;
            } else if ((c & 0xF0) == 0xE0 && i + 2 < end) {
                i += 3;
            } else if ((c & 0xF8) == 0xF0 && i + 3 < end) {
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }
}
