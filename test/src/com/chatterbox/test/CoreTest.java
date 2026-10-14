package com.chatterbox.test;

import java.util.Vector;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.IrcCase;
import com.chatterbox.core.Message;
import com.chatterbox.core.MessageRing;
import com.chatterbox.core.Str;
import com.chatterbox.core.UserEntry;
import com.chatterbox.net.FloodQueue;

import junit.framework.TestCase;

/**
 * Core-layer tests: ring buffer, IRC casemapping, string helpers, flood
 * control. Same rules as ProtocolParserTest -- no MicroEdition imports.
 */
public class CoreTest extends TestCase {

    // ---- MessageRing ----

    public void testRingKeepsOrder() {
        MessageRing ring = new MessageRing(5);
        for (int i = 0; i < 3; i++) {
            ring.add(Message.server(i * 1000, "m" + i));
        }
        assertEquals(3, ring.size());
        assertEquals("m0", ring.get(0).text);
        assertEquals("m2", ring.get(2).text);
    }

    public void testRingDropsOldestWhenFull() {
        MessageRing ring = new MessageRing(3);
        for (int i = 0; i < 5; i++) {
            ring.add(Message.server(i * 1000, "m" + i));
        }
        assertEquals(3, ring.size());
        assertEquals("m2", ring.get(0).text);
        assertEquals("m4", ring.get(2).text);
    }

    public void testRingSnapshotMatchesGet() {
        MessageRing ring = new MessageRing(4);
        for (int i = 0; i < 4; i++) {
            ring.add(Message.server(i * 1000, "m" + i));
        }
        Message[] snap = ring.snapshot();
        assertEquals(4, snap.length);
        for (int i = 0; i < snap.length; i++) {
            assertEquals("m" + i, snap[i].text);
        }
    }

    public void testRingSequenceIncreasesPastCapacity() {
        MessageRing ring = new MessageRing(2);
        ring.add(Message.server(0, "a"));
        long before = ring.sequence();
        for (int i = 0; i < 10; i++) {
            ring.add(Message.server(0, "b"));
        }
        assertTrue(ring.sequence() > before);
    }

    public void testRingClearKeepsSequence() {
        MessageRing ring = new MessageRing(2);
        ring.add(Message.server(0, "a"));
        long seq = ring.sequence();
        ring.clear();
        assertEquals(0, ring.size());
        assertEquals(seq, ring.sequence());
    }

    public void testRingIgnoresNull() {
        MessageRing ring = new MessageRing(2);
        ring.add(null);
        assertEquals(0, ring.size());
    }

    // ---- IrcCase ----

    public void testIrcCaseFolding() {
        assertTrue(IrcCase.same("Bob", "bob"));
        assertTrue(IrcCase.same("a[b]", "a{b}"));
        assertTrue(IrcCase.same("x\\y", "x|y"));
        assertTrue(IrcCase.same("m^n", "m~n"));
        assertFalse(IrcCase.same("bob", "bobby"));
        assertFalse(IrcCase.same(null, "bob"));
        assertTrue(IrcCase.same(null, null));
    }

    public void testNormalize() {
        assertEquals("bob{}", IrcCase.normalize("BoB[}"));
        assertEquals("", IrcCase.normalize(null));
    }

    // ---- Str ----

    public void testWords() {
        Vector v = Str.words("  a  bb   ccc ");
        assertEquals(3, v.size());
        assertEquals("bb", v.elementAt(1));
        assertEquals(0, Str.words("").size());
    }

    public void testSplitKeepsEmptyFields() {
        Vector v = Str.split("a,,b,", ',');
        assertEquals(4, v.size());
        assertEquals("", v.elementAt(1));
        assertEquals("", v.elementAt(3));
    }

    public void testWrapBreaksOnWords() {
        Vector v = Str.wrap("hello wonderful world", 12);
        assertTrue(v.size() >= 2);
        assertEquals("hello", v.elementAt(0));
        assertEquals("wonderful", v.elementAt(1));
    }

    public void testWrapHardBreaksLongWord() {
        Vector v = Str.wrap("abcdefghijklmnopqrstuvwxyz", 10);
        assertEquals(3, v.size());
        assertEquals("abcdefghij", v.elementAt(0));
        assertEquals("klmnopqrst", v.elementAt(1));
        assertEquals("uvwxyz", v.elementAt(2));
    }

    public void testWrapEmptyStringYieldsOneLine() {
        assertEquals(1, Str.wrap("", 20).size());
    }

    public void testFindUrl() {
        assertEquals("http://a.b/c", Str.findUrl("see http://a.b/c now"));
        assertEquals("https://x.y", Str.findUrl("(https://x.y)"));
        assertNull(Str.findUrl("no link here"));
        assertNull(Str.findUrl("http://"));
    }

    public void testClip() {
        assertEquals("abcd~", Str.clip("abcdefgh", 5));
        assertEquals("abcdefgh", Str.clip("abcdefgh", 8));
        assertEquals("ab~", Str.clip("abcdefgh", 3));
        assertEquals("", Str.clip(null, 3));
    }

    public void testStripPrefixModes() {
        assertEquals("bob", Str.stripPrefixModes("@+!bob"));
        assertEquals("bo~b", Str.stripPrefixModes("bo~b"));
        assertEquals("", Str.stripPrefixModes("@@"));
    }

    public void testContainsIgnoreCase() {
        assertTrue(Str.containsIgnoreCase("Hello Bob!", "bob"));
        assertFalse(Str.containsIgnoreCase("Hello Bob!", "bobby"));
    }

    public void testParseInt() {
        assertEquals(42, Str.parseInt("42", 0));
        assertEquals(-7, Str.parseInt("-7", 0));
        assertEquals(9, Str.parseInt("nope", 9));
        assertEquals(6697, Str.parseInt("6697", 1));
    }

    public void testParseBool() {
        assertTrue(Str.parseBool("yes", false));
        assertFalse(Str.parseBool("OFF", true));
        assertTrue(Str.parseBool("", true));
    }

    public void testTwoDigit() {
        assertEquals("00", Str.two(0));
        assertEquals("09", Str.two(9));
        assertEquals("10", Str.two(10));
        assertEquals("59", Str.two(59));
    }

    // ---- ChannelState ----

    public void testChannelUserListIsSortedAndUnique() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("zeta", true);
        c.user("Alpha", true);
        c.user("beta", true);
        c.user("alpha", true); // same nick, different case -> no duplicate
        assertEquals(3, c.userCount());
        Vector users = c.snapshotUsers();
        assertEquals("Alpha", ((UserEntry) users.elementAt(0)).getNick());
        assertEquals("beta", ((UserEntry) users.elementAt(1)).getNick());
        assertEquals("zeta", ((UserEntry) users.elementAt(2)).getNick());
    }

    public void testChannelRemoveAndRename() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("bob", true);
        assertTrue(c.renameUser("bob", "robert"));
        assertEquals(1, c.userCount());
        assertNotNull(c.findUser("ROBERT"));
        assertTrue(c.removeUser("robert"));
        assertEquals(0, c.userCount());
    }

    public void testChannelPrefixModes() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("bob", true);
        c.applyPrefixMode("bob", "+o");
        UserEntry u = c.findUser("bob");
        assertTrue(u.isOp());
        c.applyPrefixMode("bob", "-o");
        assertFalse(u.isOp());
        c.applyPrefixMode("bob", "+v");
        assertTrue(u.isVoiced());
    }

    public void testUnreadCounters() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.markUnread(false);
        c.markUnread(true);
        assertEquals(2, c.getUnread());
        assertEquals(1, c.getHighlight());
        assertEquals("!1", c.badge());
        c.clearCounters();
        assertEquals(0, c.getUnread());
        assertEquals("", c.badge());
    }

    public void testActiveWindowNeverAccumulatesUnread() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.setActive(true);
        c.markUnread(true);
        assertEquals(0, c.getUnread());
    }

    public void testUnreadIsCapped() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        for (int i = 0; i < 1500; i++) {
            c.markUnread(false);
        }
        assertEquals(999, c.getUnread());
    }

    // ---- FloodQueue ----

    public void testFloodQueueDeliversThenBlocks() {
        FloodQueue q = new FloodQueue(10, 60000, 2);
        assertTrue(q.offer("a"));
        assertTrue(q.offer("b"));
        assertEquals("a", q.poll());
        assertEquals("b", q.poll());
        // Bucket empty for a minute -> nothing more is due.
        assertNull(q.poll());
    }

    public void testFloodQueueDropsOldestWhenFull() {
        FloodQueue q = new FloodQueue(2, 60000, 4);
        q.offer("a");
        q.offer("b");
        assertFalse(q.offer("c"));
        assertEquals(1, q.droppedCount());
        assertEquals(2, q.pending());
    }

    public void testFloodQueueRefillsOverTime() throws Exception {
        FloodQueue q = new FloodQueue(10, 100, 1);
        q.offer("a");
        assertEquals("a", q.poll());
        q.offer("b");
        assertNull(q.poll());
        Thread.sleep(180);
        assertEquals("b", q.poll());
    }

    public void testFloodQueueGrantFullBurst() {
        FloodQueue q = new FloodQueue(10, 60000, 3);
        q.offer("a");
        q.offer("b");
        q.offer("c");
        q.offer("d");
        q.poll();
        q.grantFullBurst();
        assertNotNull(q.poll());
        assertNotNull(q.poll());
        assertNotNull(q.poll());
        assertNull(q.poll());
    }

    public void testFloodQueueNextDueIn() {
        FloodQueue q = new FloodQueue(10, 60000, 1);
        assertEquals(-1, q.nextDueIn());
        q.offer("a");
        assertEquals(0, q.nextDueIn());
        q.offer("b");
        q.poll();
        assertTrue(q.nextDueIn() > 0);
    }
}
