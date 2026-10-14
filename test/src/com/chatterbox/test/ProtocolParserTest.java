package com.chatterbox.test;

import java.util.Vector;

import com.chatterbox.proto.Ctcp;
import com.chatterbox.proto.IrcCodec;
import com.chatterbox.proto.IrcLine;
import com.chatterbox.proto.IrcParser;
import com.chatterbox.proto.LineReader;
import com.chatterbox.proto.Numeric;
import com.chatterbox.proto.Utf8;

import junit.framework.TestCase;

/**
 * Parser / codec tests. JUnit 3 style, runs on any desktop JDK.
 *
 * Run with:  java -cp build/classes:build/stub com.chatterbox.test.TestMain
 * or via ant:  ant test
 *
 * This class touches ONLY com.chatterbox.proto, which by design has no
 * javax.microedition imports. That is the whole point of the layering.
 */
public class ProtocolParserTest extends TestCase {

    // ------------------------------------------------------------------
    // Line parsing
    // ------------------------------------------------------------------

    public void testSimpleCommand() {
        IrcLine l = IrcParser.parse("PING :1234567890");
        assertNotNull(l);
        assertNull(l.getPrefix());
        assertEquals("PING", l.getCommand());
        assertEquals("1234567890", l.getTrailing());
        assertEquals(0, l.getParamCount());
    }

    public void testPrefixAndParams() {
        IrcLine l = IrcParser.parse(":nick!user@host PRIVMSG #chan :hello there");
        assertEquals("nick!user@host", l.getPrefix());
        assertEquals("PRIVMSG", l.getCommand());
        assertEquals(1, l.getParamCount());
        assertEquals("#chan", l.getParam(0));
        assertEquals("hello there", l.getTrailing());
        assertEquals("nick", l.getPrefixNick());
        assertEquals("user", l.getPrefixUser());
        assertEquals("host", l.getPrefixHost());
    }

    public void testTrailingWithSpacesAndColons() {
        IrcLine l = IrcParser.parse(":a!b@c PRIVMSG #x ::-)  spaced :  out  ");
        assertEquals(":-)  spaced :  out  ", l.getTrailing());
        assertEquals(1, l.getParamCount());
    }

    public void testEmptyTrailing() {
        IrcLine l = IrcParser.parse("PRIVMSG #x :");
        assertNotNull(l);
        assertEquals("", l.getTrailing());
    }

    public void testNoTrailingMultiParams() {
        IrcLine l = IrcParser.parse(":op MODE #chan +o someone");
        assertEquals("MODE", l.getCommand());
        assertEquals(3, l.getParamCount());
        assertEquals("#chan", l.getParam(0));
        assertEquals("+o", l.getParam(1));
        assertEquals("someone", l.getParam(2));
        assertNull(l.getTrailing());
    }

    public void testServerNumeric() {
        IrcLine l = IrcParser.parse(":irc.example.net 353 me = #chan :@op +voiced regular");
        assertEquals("353", l.getCommand());
        assertTrue(IrcParser.isNumeric(l.getCommand()));
        assertEquals("irc.example.net", l.getPrefix());
        assertEquals(3, l.getParamCount());
        assertEquals("me", l.getParam(0));
        assertEquals("=", l.getParam(1));
        assertEquals("#chan", l.getParam(2));
        assertEquals("@op +voiced regular", l.getTrailing());
        assertTrue(l.isFromServer());
    }

    public void testNumericDetection() {
        assertTrue(IrcParser.isNumeric("001"));
        assertTrue(IrcParser.isNumeric("433"));
        assertFalse(IrcParser.isNumeric("PRIV"));
        assertFalse(IrcParser.isNumeric("1234"));
        assertFalse(IrcParser.isNumeric(null));
    }

    public void testLowercaseCommandIsUppercased() {
        assertEquals("PRIVMSG", IrcParser.parse("privmsg #a :b").getCommand());
    }

    public void testMalformedLinesReturnNull() {
        assertNull(IrcParser.parse(null));
        assertNull(IrcParser.parse(""));
        assertNull(IrcParser.parse("   "));
        assertNull(IrcParser.parse(":onlyprefix"));
    }

    public void testCrLfStripped() {
        assertNotNull(IrcParser.parse("PING :x\r"));
        assertEquals("x", IrcParser.parse("PING :x\r").getTrailing());
    }

    public void testServerNamePrefixDetection() {
        assertTrue(IrcParser.parse(":irc.net 001 me :hi").isFromServer());
        assertFalse(IrcParser.parse(":nick PRIVMSG #a :b").isFromServer());
        assertFalse(IrcParser.parse(":nick!u@h PRIVMSG #a :b").isFromServer());
        assertTrue(IrcParser.parse("PING :x").isFromServer());
    }

    public void testNickExtraction() {
        assertEquals("bob", IrcLine.nickOf("bob!user@host"));
        assertEquals("bob", IrcLine.nickOf("bob"));
        assertEquals("bob", IrcLine.nickOf("bob@host"));
        assertNull(IrcLine.nickOf(null));
    }

    // ------------------------------------------------------------------
    // Round-trip serialisation
    // ------------------------------------------------------------------

    public void testSerializeRoundTrip() {
        String[] samples = {
            "PING :12345",
            ":nick!user@host PRIVMSG #chan :hello there",
            ":irc.net 353 me = #chan :@a +b c",
            "MODE #chan +o nick",
            "QUIT :bye now"
        };
        for (int i = 0; i < samples.length; i++) {
            IrcLine l = IrcParser.parse(samples[i]);
            assertEquals(samples[i], l.encode());
        }
    }

    public void testSerializeAddsCrLf() {
        String out = IrcCodec.pong("abc");
        assertEquals("PONG :abc\r\n", out);
    }

    public void testLongLineIsTruncatedTo512Bytes() {
        StringBuffer big = new StringBuffer();
        for (int i = 0; i < 900; i++) {
            big.append('x');
        }
        String line = IrcCodec.privmsg("#chan", big.toString());
        byte[] bytes = Utf8.encode(line);
        assertTrue("line was " + bytes.length + " bytes", bytes.length <= 512);
        assertTrue(line.endsWith("\r\n"));
    }

    public void testBuilders() {
        assertEquals("NICK :bob\r\n", IrcCodec.nick("bob"));
        assertEquals("JOIN :#a\r\n", IrcCodec.join("#a"));
        assertEquals("JOIN #a key\r\n", IrcCodec.join("#a", "key"));
        assertEquals("PART #a :bye\r\n", IrcCodec.part("#a", "bye"));
        assertEquals("TOPIC #a :new topic\r\n", IrcCodec.topic("#a", "new topic"));
        assertEquals("KICK #a bob :rude\r\n", IrcCodec.kick("#a", "bob", "rude"));
        assertEquals("WHOIS :bob\r\n", IrcCodec.whois("bob"));
        assertEquals("MODE #a\r\n", IrcCodec.mode("#a", null));
        assertEquals("NAMES #a\r\n", IrcCodec.names("#a"));
    }

    public void testActionIsCtcpWrapped() {
        String line = IrcCodec.action("#chan", "waves");
        assertEquals("PRIVMSG #chan :" + Ctcp.wrap("ACTION", "waves") + "\r\n", line);
        IrcLine l = IrcParser.parse(line);
        assertTrue(Ctcp.isCtcp(l.getTrailing()));
        assertTrue(Ctcp.isAction(l.getTrailing()));
        assertEquals("waves", Ctcp.body(l.getTrailing()));
    }

    // ------------------------------------------------------------------
    // CTCP
    // ------------------------------------------------------------------

    public void testCtcpDetection() {
        assertTrue(Ctcp.isCtcp(Ctcp.wrap("VERSION", "")));
        assertFalse(Ctcp.isCtcp("plain text"));
        assertFalse(Ctcp.isCtcp(null));
        assertFalse(Ctcp.isCtcp(""));
        assertFalse(Ctcp.isCtcp(""));
    }

    public void testCtcpVersionReply() {
        String reply = IrcCodec.versionReply("bob", "Chatterbox 1.0");
        IrcLine l = IrcParser.parse(reply);
        assertEquals("NOTICE", l.getCommand());
        assertEquals("bob", l.getParam(0));
        assertTrue(Ctcp.isVersionQuery(l.getTrailing()));
        assertEquals("Chatterbox 1.0", Ctcp.body(l.getTrailing()));
    }

    public void testCtcpUnwrapPlain() {
        assertEquals("hi", Ctcp.unwrap("hi"));
        assertEquals("hi", Ctcp.unwrap(Ctcp.wrap("ACTION", "hi")));
    }

    // ------------------------------------------------------------------
    // Byte framing (LineReader)
    // ------------------------------------------------------------------

    public void testLineReaderSplitsOnCrLf() {
        LineReader r = new LineReader();
        Vector lines = r.feed(Utf8.encode("PING :a\r\nPONG :b\r\n"));
        assertEquals(2, lines.size());
        assertEquals("PING :a", lines.elementAt(0));
        assertEquals("PONG :b", lines.elementAt(1));
    }

    public void testLineReaderBuffersPartialLines() {
        LineReader r = new LineReader();
        assertEquals(0, r.feed(Utf8.encode("PIN")).size());
        assertEquals(0, r.feed(Utf8.encode("G :a\r")).size());
        Vector lines = r.feed(Utf8.encode("\n"));
        assertEquals(1, lines.size());
        assertEquals("PING :a", lines.elementAt(0));
    }

    public void testLineReaderAcceptsBareLf() {
        LineReader r = new LineReader();
        Vector lines = r.feed(Utf8.encode("A :1\nB :2\n"));
        assertEquals(2, lines.size());
    }

    public void testLineReaderResyncsAfterOverlongLine() {
        LineReader r = new LineReader();
        StringBuffer big = new StringBuffer();
        for (int i = 0; i < 700; i++) {
            big.append('y');
        }
        Vector lines = r.feed(Utf8.encode(big.toString() + "\r\nPING :ok\r\n"));
        // The over-long line is dropped, the next one survives.
        assertEquals(1, lines.size());
        assertEquals("PING :ok", lines.elementAt(0));
        assertTrue(r.hadOverflow());
    }

    public void testLineReaderFlushReturnsPartialTail() {
        LineReader r = new LineReader();
        r.feed(Utf8.encode("PING :partial"));
        Vector lines = r.flush();
        assertEquals(1, lines.size());
        assertEquals("PING :partial", lines.elementAt(0));
    }

    public void testLineReaderZeroChunksAreSafe() {
        LineReader r = new LineReader();
        assertEquals(0, r.feed(new byte[0]).size());
        assertEquals(0, r.feed(null, 0, 0).size());
    }

    // ------------------------------------------------------------------
    // UTF-8
    // ------------------------------------------------------------------

    public void testUtf8RoundTrip() {
        String[] samples = {
            "hello",
            "café",            // latin accents
            "Αθήνα",           // greek
            "北京",            // CJK
            "😀",              // surrogate pair
            ""                                     // empty
        };
        for (int i = 0; i < samples.length; i++) {
            assertEquals(samples[i], Utf8.decode(Utf8.encode(samples[i])));
        }
    }

    public void testUtf8MalformedDoesNotThrow() {
        byte[] bad = { (byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE, 'o', 'k' };
        String s = Utf8.decode(bad);
        assertTrue(s.indexOf("ok") >= 0);
    }

    public void testUtf8TruncatedSequenceIsSafe() {
        byte[] truncated = { (byte) 0xE2, (byte) 0x82 };
        assertEquals(1, Utf8.decode(truncated).length());
    }

    public void testLatinFallbackForInvalidUtf8() {
        LineReader r = new LineReader();
        // 0xE9 alone is invalid UTF-8, so it should fall back to latin-1.
        byte[] data = { (byte) 0xE9, '\r', '\n' };
        Vector lines = r.feed(data);
        assertEquals(1, lines.size());
        assertEquals("é", lines.elementAt(0));
    }

    public void testUtf8LooksUtf8() {
        assertTrue(Utf8.looksUtf8(Utf8.encode("café"), 0, 5));
        assertFalse(Utf8.looksUtf8(new byte[] { (byte) 0xE9 }, 0, 1));
        assertTrue(Utf8.looksUtf8(Utf8.encode("plain ascii"), 0, 11));
    }

    // ------------------------------------------------------------------
    // Numeric helper
    // ------------------------------------------------------------------

    public void testNumericClassification() {
        assertTrue(Numeric.isError("433"));
        assertTrue(Numeric.isError("474"));
        assertFalse(Numeric.isError("353"));
        assertFalse(Numeric.isError("PRIV"));
        assertTrue(Numeric.isReply("001"));
        assertTrue(Numeric.isHandled("433"));
        assertTrue(Numeric.isHandled("353"));
        assertTrue(Numeric.isHandled("366"));
    }
}
