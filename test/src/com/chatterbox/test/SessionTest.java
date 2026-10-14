package com.chatterbox.test;

import com.chatterbox.core.Sender;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.Session;
import com.chatterbox.core.ChannelState;
import com.chatterbox.core.IrcEventListener;
import com.chatterbox.core.Message;
import com.chatterbox.core.IgnoreList;
import com.chatterbox.core.HighlightRules;
import com.chatterbox.core.ServiceIdentify;
import com.chatterbox.core.MessageHistory;
import com.chatterbox.core.CommandProcessor;
import com.chatterbox.ui.InputLine;
import com.chatterbox.ui.NickComplete;

import java.util.Vector;

import junit.framework.TestCase;

/**
 * Session-level tests driven through {@link Session#feedLine(String)} with a
 * fake Sender. This is the layer where protocol bugs actually bite, so it is
 * worth testing without a socket.
 */
public class SessionTest extends TestCase {

    /** Records what the session tried to send. */
    static final class FakeSender implements Sender {
        final Vector lines = new Vector();
        boolean connected = true;

        public void send(String line) {
            lines.addElement(line);
        }

        public boolean isConnected() {
            return connected;
        }

        public void close() {
            connected = false;
        }

        String last() {
            return lines.size() == 0 ? "" : (String) lines.elementAt(lines.size() - 1);
        }

        boolean contains(String s) {
            for (int i = 0; i < lines.size(); i++) {
                if (((String) lines.elementAt(i)).indexOf(s) >= 0) {
                    return true;
                }
            }
            return false;
        }

        void clear() {
            lines.removeAllElements();
        }
    }

    /** Captures the callbacks so we can assert on alerts and highlights. */
    static final class Recorder implements IrcEventListener {
        int messages;
        int alerts;
        int mentions;
        int privates;
        int stateChanges;
        int nickInUse;
        int windowsOpened;
        int errors;
        long lastLag = -1;
        String lastTopic;
        String lastError;

        public void onMessage(ServerState s, ChannelState w, Message m) {
            messages++;
        }

        public void onStateChanged(ServerState s, int state, String text) {
            stateChanges++;
        }

        public void onNickInUse(ServerState s, String attempted, String chosen) {
            nickInUse++;
        }

        public void onWindowOpened(ServerState s, ChannelState w) {
            windowsOpened++;
        }

        public void onWindowClosed(ServerState s, ChannelState w) {
        }

        public void onTopicChanged(ServerState s, ChannelState c, String topic) {
            lastTopic = topic;
        }

        public void onError(ServerState s, String reason) {
            errors++;
            lastError = reason;
        }

        public void onAlert(ServerState s, ChannelState w, boolean mention, boolean priv) {
            alerts++;
            if (mention) {
                mentions++;
            }
            if (priv) {
                privates++;
            }
        }

        public void onLag(ServerState s, long millis) {
            lastLag = millis;
        }
    }

    private ServerState server;
    private FakeSender sender;
    private Session session;
    private Recorder rec;

    public void setUp() {
        server = new ServerState("test:6667", "irc.test", 6667, false);
        server.setNick("me");
        server.setAltNick("me_");
        sender = new FakeSender();
        session = new Session(server, sender);
        rec = new Recorder();
        session.setListener(rec);
    }

    /** Run the registration handshake so the server knows our nick. */
    private void register() {
        session.register();
        session.feedLine(":irc.test 001 me :Welcome");
    }

    // ---- registration ----

    public void testRegisterSendsNickAndUser() {
        session.register();
        assertTrue(sender.contains("NICK :me\r\n"));
        assertTrue(sender.contains("USER "));
    }

    public void testRegisterSanitisesNick() {
        server.setNick("bad nick!");
        session.register();
        assertTrue(sender.contains("NICK :badnick\r\n"));
    }

    public void testRegisterPrefixesLeadingDigit() {
        server.setNick("1abc");
        session.register();
        assertTrue(sender.contains("NICK :_1abc\r\n"));
    }

    public void testWelcomeMarksConnectedAndReplaysAutoJoin() {
        session.setAutoJoin("#a,#b keyb");
        register();
        assertTrue(session.isRegistered());
        assertEquals(ServerState.CONNECTED, server.getState());
        assertTrue(sender.contains("JOIN :#a"));
        assertTrue(sender.contains("JOIN #b keyb"));
    }

    public void testNickCollisionFallsBackToAltNick() {
        session.register();
        session.feedLine(":irc.test 433 * me :Nickname is already in use");
        assertEquals(1, rec.nickInUse);
        assertEquals("me_", server.getSelfNick());
        assertTrue(sender.contains("NICK :me_\r\n"));
    }

    public void testSecondNickCollisionAppendsDigits() {
        session.register();
        session.feedLine(":irc.test 433 * me :in use");
        session.feedLine(":irc.test 433 * me_ :in use");
        assertEquals("me_2", server.getSelfNick());
    }

    // ---- PING / PONG ----

    public void testPingIsAnswered() {
        session.feedLine("PING :abc123");
        assertEquals("PONG :abc123\r\n", sender.last());
    }

    public void testPingWithNoTokenUsesServerHost() {
        session.feedLine("PING");
        assertEquals("PONG :irc.test\r\n", sender.last());
    }

    public void testKeepalivePingMeasuresLag() throws Exception {
        register();
        session.sendKeepalivePing();
        assertTrue(sender.contains("PING :cb"));
        Thread.sleep(15);
        session.feedLine(":irc.test PONG irc.test :cb");
        assertTrue(rec.lastLag >= 0);
    }

    // ---- channels ----

    public void testJoinAddsUserAndEvent() {
        register();
        session.feedLine(":bob!b@h JOIN :#chan");
        ChannelState c = server.window("#chan");
        assertNotNull(c);
        assertEquals(1, c.userCount());
        assertNotNull(c.findUser("bob"));
        assertEquals(1, c.getRing().size());
    }

    public void testSelfJoinRecordsIdent() {
        register();
        session.feedLine(":me!myuser@myhost JOIN :#chan");
        assertEquals("myuser", server.getSelfUser());
        assertEquals("myhost", server.getSelfHost());
        assertTrue(server.window("#chan").findUser("me").isSelf());
    }

    public void testNamesReplyPopulatesList() {
        register();
        session.feedLine(":me JOIN :#chan");
        session.feedLine(":irc.test 353 me = #chan :@op +voice plain");
        ChannelState c = server.window("#chan");
        // 4 = our own nick from the JOIN plus the three from 353.
        assertEquals(4, c.userCount());
        assertTrue(c.findUser("op").isOp());
        assertTrue(c.findUser("voice").isVoiced());
        assertEquals("", c.findUser("plain").getPrefix());
    }

    public void testNamesReplySplitAcrossMultipleNumerics() {
        register();
        session.feedLine(":me JOIN :#chan");
        session.feedLine(":irc.test 353 me = #chan :@a b");
        session.feedLine(":irc.test 353 me = #chan :c d");
        // me + a + b + c + d
        assertEquals(5, server.window("#chan").userCount());
    }

    public void testPartRemovesUser() {
        register();
        session.feedLine(":bob JOIN :#chan");
        session.feedLine(":bob PART #chan :bye");
        assertEquals(0, server.window("#chan").findUser("bob") == null ? 0 : 1);
    }

    public void testQuitRemovesUserFromAllChannels() {
        register();
        session.feedLine(":bob JOIN :#a");
        session.feedLine(":bob JOIN :#b");
        session.feedLine(":bob!b@h QUIT :connection reset");
        assertNull(server.window("#a").findUser("bob"));
        assertNull(server.window("#b").findUser("bob"));
    }

    public void testNickChangeRenamesEverywhere() {
        register();
        session.feedLine(":bob JOIN :#a");
        session.feedLine(":bob!b@h NICK :robert");
        assertNotNull(server.window("#a").findUser("robert"));
        assertNull(server.window("#a").findUser("bob"));
    }

    public void testSelfNickChangeUpdatesServerState() {
        register();
        session.feedLine(":me!u@h NICK :newme");
        assertEquals("newme", server.getSelfNick());
    }

    public void testKickRemovesVictim() {
        register();
        session.feedLine(":op JOIN :#c");
        session.feedLine(":me JOIN :#c");
        session.feedLine(":op KICK #c me :behave");
        assertTrue(server.window("#c").isParted());
    }

    public void testKickRemovesOtherUser() {
        register();
        session.feedLine(":bob JOIN :#c");
        session.feedLine(":op KICK #c bob :bye");
        assertNull(server.window("#c").findUser("bob"));
    }

    public void testModeOpGrantAndRevoke() {
        register();
        session.feedLine(":bob JOIN :#c");
        session.feedLine(":op MODE #c +o bob");
        assertTrue(server.window("#c").findUser("bob").isOp());
        session.feedLine(":op MODE #c -o bob");
        assertFalse(server.window("#c").findUser("bob").isOp());
    }

    public void testTopicNumerics() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":irc.test 332 me #c :the topic");
        assertEquals("the topic", server.window("#c").getTopic());
        assertEquals("the topic", rec.lastTopic);
        session.feedLine(":irc.test 331 me #c");
        assertEquals("", server.window("#c").getTopic());
    }

    public void testTopicCommandUpdatesState() {
        register();
        session.feedLine(":op!o@h TOPIC #c :new stuff");
        assertEquals("new stuff", server.window("#c").getTopic());
    }

    public void testEndOfNamesAddsSyntheticCount() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":irc.test 353 me = #c :@a b");
        session.feedLine(":irc.test 366 me #c :End of /NAMES list.");
        assertTrue(server.window("#c").getRing().size() >= 1);
        assertTrue(server.window("#c").getRing().get(0).text.indexOf("users") >= 0);
    }

    // ---- messaging ----

    public void testChannelPrivmsgGoesToChannelWindow() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":bob!b@h PRIVMSG #c :hello");
        ChannelState c = server.window("#c");
        assertEquals(1, c.getRing().size());
        assertEquals("hello", c.getRing().get(0).text);
        assertEquals(0, rec.alerts);
    }

    public void testMentionRaisesAlert() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":bob!b@h PRIVMSG #c :hey me, look");
        assertEquals(1, rec.mentions);
        assertTrue(server.window("#c").getRing().get(0).highlight);
    }

    public void testOwnMessageDoesNotRaiseAlert() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":me!u@h PRIVMSG #c :me talking to myself");
        assertEquals(0, rec.alerts);
    }

    public void testNoticeDoesNotHighlight() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":bob!b@h NOTICE #c :me: server said hi");
        assertEquals(0, rec.mentions);
    }

    public void testPrivateMessageOpensWindowAndAlerts() {
        register();
        session.feedLine(":bob!b@h PRIVMSG me :psst");
        assertEquals(1, rec.windowsOpened);
        assertEquals(1, rec.privates);
        ChannelState w = server.window("bob");
        assertNotNull(w);
        assertEquals("psst", w.getRing().get(0).text);
    }

    public void testSecondPrivateMessageDoesNotReopenWindow() {
        register();
        session.feedLine(":bob!b@h PRIVMSG me :one");
        session.feedLine(":bob!b@h PRIVMSG me :two");
        assertEquals(1, rec.windowsOpened);
        assertEquals(2, server.window("bob").getRing().size());
    }

    public void testActionIsUnwrapped() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":bob!b@h PRIVMSG #c :\u0001ACTION waves\u0001");
        assertEquals(Message.ACTION, server.window("#c").getRing().get(0).kind);
        assertEquals("waves", server.window("#c").getRing().get(0).text);
    }

    public void testVersionQueryIsAnsweredNotEchoed() {
        register();
        sender.clear();
        session.feedLine(":bob!b@h PRIVMSG me :\u0001VERSION\u0001");
        assertTrue(sender.contains("NOTICE bob :"));
        // A VERSION query must not create a window or echo into one.
        assertNull(server.window("bob"));
    }

    public void testCtcpPingIsAnswered() {
        register();
        sender.clear();
        session.feedLine(":bob!b@h PRIVMSG me :\u0001PING 123\u0001");
        assertTrue(sender.contains("PONG 123"));
    }

    public void testUnknownCtcpIsSilentlyDropped() {
        register();
        session.feedLine(":bob!b@h PRIVMSG me :\u0001FLOOBLE x\u0001");
        ChannelState w = server.window("bob");
        assertTrue(w == null || w.getRing().size() == 0);
    }

    public void testMalformedLinesDoNotThrow() {
        register();
        String[] junk = {
            "", "   ", ":", "::::", ":only", "PRIVMSG", "PRIVMSG #a",
            ":a!b@c PRIVMSG", "KICK", "MODE", "353", "001", "TOPIC",
            ":x NICK", ":x JOIN", ":x PART", ":x KICK #a"
        };
        for (int i = 0; i < junk.length; i++) {
            session.feedLine(junk[i]); // must not throw
        }
        assertTrue(rec.errors >= 0);
    }

    public void testErrorLineRaisesOnError() {
        register();
        session.feedLine("ERROR :Closing link");
        assertEquals(1, rec.errors);
        assertEquals("Closing link", rec.lastError);
    }

    public void testPasswdMismatchRaisesError() {
        register();
        session.feedLine(":irc.test 464 * :Password incorrect");
        assertEquals(1, rec.errors);
    }

    public void testNoSuchChannelPostsMessage() {
        register();
        session.feedLine(":irc.test 403 me #nope :No such channel");
        assertTrue(rec.messages > 0);
    }

    // ---- CommandProcessor ----

    public void testPlainTextSendsPrivmsgToChannel() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        ChannelState c = server.window("#c");
        CommandProcessor cp = new CommandProcessor(server, session, sender);
        CommandProcessor.Result r = cp.execute(c, "hello all");
        assertTrue(sender.contains("PRIVMSG #c :hello all"));
        assertEquals(1, c.getRing().size());
        assertTrue(r.handled);
    }

    public void testJoinCommandSendsJoinAndFocuses() {
        register();
        sender.clear();
        CommandProcessor cp = new CommandProcessor(server, session, sender);
        CommandProcessor.Result r = cp.execute(null, "/join #new");
        assertTrue(sender.contains("JOIN :#new"));
        assertEquals("#new", r.focus.getName());
    }

    public void testPartCommand() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        new CommandProcessor(server, session, sender).execute(server.window("#c"), "/part");
        assertTrue(sender.contains("PART #c"));
        assertTrue(server.window("#c").isParted());
    }

    public void testMeCommandUsesCtcp() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        new CommandProcessor(server, session, sender)
                .execute(server.window("#c"), "/me waves");
        assertTrue(sender.contains("ACTION"));
    }

    public void testMsgCommandOpensPrivateWindow() {
        register();
        sender.clear();
        CommandProcessor.Result r = new CommandProcessor(server, session, sender)
                .execute(null, "/msg bob hi there");
        assertTrue(sender.contains("PRIVMSG bob :hi there"));
        assertEquals("bob", r.focus.getName());
    }

    public void testNickCommand() {
        register();
        sender.clear();
        new CommandProcessor(server, session, sender).execute(null, "/nick zzz");
        assertTrue(sender.contains("NICK :zzz"));
    }

    public void testTopicCommand() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        new CommandProcessor(server, session, sender)
                .execute(server.window("#c"), "/topic hello world");
        assertTrue(sender.contains("TOPIC #c :hello world"));
    }

    public void testTopicCommandRejectsPrivateWindow() {
        register();
        sender.clear();
        CommandProcessor.Result r = new CommandProcessor(server, session, sender)
                .execute(server.window("bob", ChannelState.TYPE_PRIVATE), "/topic x");
        assertEquals("", sender.last());
        assertTrue(r.notice.length() > 0);
    }

    public void testQueryCommandFocusesWithoutSending() {
        register();
        sender.clear();
        CommandProcessor.Result r = new CommandProcessor(server, session, sender)
                .execute(null, "/query bob");
        assertEquals(0, sender.lines.size());
        assertEquals("bob", r.focus.getName());
    }

    public void testWhoisCommand() {
        register();
        sender.clear();
        new CommandProcessor(server, session, sender).execute(null, "/whois bob");
        assertTrue(sender.contains("WHOIS :bob"));
    }

    public void testClearCommandEmptiesRing() {
        register();
        session.feedLine(":me JOIN :#c");
        session.feedLine(":bob PRIVMSG #c :hi");
        ChannelState c = server.window("#c");
        CommandProcessor.Result r = new CommandProcessor(server, session, sender).execute(c, "/clear");
        assertEquals(0, c.getRing().size());
        assertTrue(r.clear);
    }

    public void testQuitCommandSetsFlag() {
        register();
        sender.clear();
        CommandProcessor.Result r = new CommandProcessor(server, session, sender)
                .execute(null, "/quit see ya");
        assertTrue(r.quit);
        assertTrue(sender.contains("QUIT :see ya"));
    }

    public void testCycleCommand() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        new CommandProcessor(server, session, sender).execute(server.window("#c"), "/cycle");
        assertTrue(sender.contains("PART #c :cycling"));
        assertTrue(sender.contains("JOIN :#c"));
    }

    public void testAwayAndBack() {
        register();
        sender.clear();
        CommandProcessor cp = new CommandProcessor(server, session, sender);
        cp.execute(null, "/away lunch");
        assertTrue(sender.contains("AWAY :lunch"));
        sender.clear();
        cp.execute(null, "/back");
        assertTrue(sender.contains("AWAY\r\n"));
    }

    public void testUnknownCommandIsReportedNotSent() {
        register();
        sender.clear();
        CommandProcessor.Result r = new CommandProcessor(server, session, sender)
                .execute(null, "/flurb");
        assertEquals(0, sender.lines.size());
        assertTrue(r.notice.indexOf("/flurb") >= 0);
    }

    public void testKickCommand() {
        register();
        session.feedLine(":me JOIN :#c");
        sender.clear();
        new CommandProcessor(server, session, sender)
                .execute(server.window("#c"), "/kick bob spam");
        assertTrue(sender.contains("KICK #c bob :spam"));
    }

    public void testRawCommandSendsVerbatim() {
        register();
        sender.clear();
        new CommandProcessor(server, session, sender).execute(null, "/raw PRIVMSG #x :hi");
        assertEquals("PRIVMSG #x :hi\r\n", sender.last());
    }

    // ---- InputLine ----

    public void testInputLineEditing() {
        InputLine in = new InputLine();
        in.insert("hello world");
        assertEquals("hello world", in.getText());
        in.moveHome();
        in.moveRight();   // caret between h and e
        in.backspace();
        assertEquals("ello world", in.getText());
        in.moveEnd();
        in.delete();
        assertEquals("ello world", in.getText()); // delete at end does nothing
    }

    public void testInputLineClearWord() {
        InputLine in = new InputLine();
        in.insert("one two three");
        in.moveHome();
        in.moveRight();
        in.moveRight();
        in.moveRight();   // caret just past "one "
        in.clearWord();
        assertEquals("two three", in.getText());
    }

    public void testInputLineCurrentWord() {
        InputLine in = new InputLine();
        in.insert("hi bo");
        assertEquals("bo", in.currentWord());
        in.insert("b");
        assertEquals("bob", in.currentWord());
    }

    public void testInputLineCompletionCycles() {
        InputLine in = new InputLine();
        in.insert("bo");
        String[] m = { "bob", "bobby", "bobby2" };
        in.complete(m);
        assertEquals("bob", in.getText());
        in.complete(m);
        assertEquals("bobby", in.getText());
        in.complete(m);
        assertEquals("bobby2", in.getText());
        in.complete(m);
        assertEquals("bob", in.getText());
    }

    public void testInputLineCompletionPreservesPrefix() {
        InputLine in = new InputLine();
        in.insert("hey bo");
        in.complete(new String[] { "bob:" });
        assertEquals("hey bob:", in.getText());
    }

    public void testInputLineMaxLength() {
        InputLine in = new InputLine();
        for (int i = 0; i < 600; i++) {
            in.insert('x');
        }
        assertEquals(InputLine.MAX_INPUT, in.length());
    }

    public void testInputLineConsumeResets() {
        InputLine in = new InputLine();
        in.insert("abc");
        assertEquals("abc", in.consume());
        assertTrue(in.isEmpty());
    }

    // ---- NickComplete ----

    public void testNickCompletionMatches() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("bob", true);
        c.user("bobby", true);
        c.user("alice", true);
        String[] m = NickComplete.matches(c, "bo");
        assertEquals(2, m.length);
        assertEquals("bob", m[0]);
        assertEquals("bobby", m[1]);
    }

    public void testNickCompletionIsCaseInsensitive() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("Bob", true);
        assertEquals(1, NickComplete.matches(c, "bo").length);
    }

    public void testNickCompletionNoMatch() {
        ChannelState c = new ChannelState("#x", ChannelState.TYPE_CHANNEL);
        c.user("bob", true);
        assertEquals(0, NickComplete.matches(c, "zz").length);
        assertEquals(0, NickComplete.matches(c, "").length);
        assertEquals(0, NickComplete.matches(null, "b").length);
    }

    public void testNickCompletionSingleMatchGetsSeparator() {
        String[] d = NickComplete.decorate(new String[] { "bob" }, "bo");
        assertEquals(1, d.length);
        assertEquals("bob: ", d[0]);
    }

    public void testShouldCompleteOnlyAtWordStart() {
        InputLine in = new InputLine();
        in.insert("bo");
        assertTrue(NickComplete.shouldComplete(in));
        in.insert("x");
        in.moveHome();
        in.moveRight();
        in.moveRight();
        assertFalse(NickComplete.shouldComplete(in));
    }

    // ---- IgnoreList tests ----

    public void testIgnoreListBasic() {
        IgnoreList il = new IgnoreList();
        assertEquals(0, il.size());
        il.add("spammer");
        assertEquals(1, il.size());
        assertTrue(il.isIgnored("spammer", "user", "host"));
        assertFalse(il.isIgnored("other", "user", "host"));
        il.remove("spammer");
        assertEquals(0, il.size());
        assertFalse(il.isIgnored("spammer", "user", "host"));
    }

    public void testIgnoreListWildcard() {
        IgnoreList il = new IgnoreList();
        il.add("*spam*");
        assertTrue(il.isIgnored("spammer", "u", "h"));
        assertTrue(il.isIgnored("spam", "u", "h"));
        assertTrue(il.isIgnored("nospam", "u", "h"));
        assertFalse(il.isIgnored("other", "u", "h"));
    }

    public void testIgnoreListHostMask() {
        IgnoreList il = new IgnoreList();
        il.add("user@*.evil.com");
        assertTrue(il.isIgnored("nick", "user", "host.evil.com"));
        assertTrue(il.isIgnored("nick2", "user", "another.evil.com"));
        assertFalse(il.isIgnored("nick", "user", "good.com"));
        assertFalse(il.isIgnored("nick", "other", "host.evil.com"));
    }

    public void testIgnoreListMaxEntries() {
        IgnoreList il = new IgnoreList();
        for (int i = 0; i < 60; i++) {
            il.add("nick" + i);
        }
        assertEquals(50, il.size()); // MAX_ENTRIES = 50
    }

    // ---- HighlightRules tests ----

    public void testHighlightRulesBasic() {
        HighlightRules hr = new HighlightRules();
        hr.add("urgent");
        assertNotNull(hr.check("this is urgent", "nick", "u", "h"));
        assertNull(hr.check("this is not", "nick", "u", "h"));
    }

    public void testHighlightRulesWholeWord() {
        HighlightRules hr = new HighlightRules();
        HighlightRules.Rule r = new HighlightRules.Rule("cat");
        r.wholeWord = true;
        hr.add(r);
        assertNotNull(hr.check("my cat", "n", "u", "h"));
        assertNotNull(hr.check("cat!", "n", "u", "h"));
        assertNull(hr.check("category", "n", "u", "h"));
        assertNull(hr.check("scatter", "n", "u", "h"));
    }

    public void testHighlightRulesCaseSensitive() {
        HighlightRules hr = new HighlightRules();
        HighlightRules.Rule r = new HighlightRules.Rule("URGENT");
        r.caseSensitive = true;
        hr.add(r);
        assertNotNull(hr.check("URGENT help", "n", "u", "h"));
        assertNull(hr.check("urgent help", "n", "u", "h"));
    }

    public void testHighlightRulesHostMatch() {
        HighlightRules hr = new HighlightRules();
        HighlightRules.Rule r = new HighlightRules.Rule("evil.com");
        r.matchHost = true;
        hr.add(r);
        assertNotNull(hr.check("hello", "nick", "user", "evil.com"));
        assertNull(hr.check("hello", "nick", "user", "good.com"));
    }

    public void testHighlightRulesReplacesDuplicate() {
        HighlightRules hr = new HighlightRules();
        hr.add("test");
        hr.add("test"); // same pattern, should replace
        assertEquals(1, hr.size());
    }

    // ---- ServiceIdentify tests ----

    public void testServiceIdentifyBasic() {
        FakeSender sender = new FakeSender();
        Session session = new Session(server, sender);
        ServiceIdentify si = session.getServiceIdentify();
        
        ServiceIdentify.Service ns = new ServiceIdentify.Service("NickServ");
        ns.password = "secret";
        si.addService(ns);
        
        assertEquals(1, si.getServices().size());
        
        session.register();
        session.feedLine(":irc.test 001 me :Welcome");
        
        assertTrue(sender.contains("PRIVMSG NickServ :IDENTIFY me secret"));
    }

    public void testServiceIdentifyMultiple() {
        FakeSender sender = new FakeSender();
        Session session = new Session(server, sender);
        ServiceIdentify si = session.getServiceIdentify();
        
        ServiceIdentify.Service ns = new ServiceIdentify.Service("NickServ");
        ns.password = "pass1";
        si.addService(ns);
        
        ServiceIdentify.Service cs = new ServiceIdentify.Service("ChanServ");
        cs.password = "pass2";
        si.addService(cs);
        
        assertEquals(2, si.getServices().size());
        
        session.register();
        session.feedLine(":irc.test 001 me :Welcome");
        
        assertTrue(sender.contains("PRIVMSG NickServ :IDENTIFY me pass1"));
        assertTrue(sender.contains("PRIVMSG ChanServ :IDENTIFY me pass2"));
    }

    public void testServiceIdentifyReplacesByName() {
        ServiceIdentify si = new ServiceIdentify(new Session(server, new FakeSender()));
        ServiceIdentify.Service s1 = new ServiceIdentify.Service("NickServ");
        s1.password = "old";
        si.addService(s1);
        
        ServiceIdentify.Service s2 = new ServiceIdentify.Service("NickServ");
        s2.password = "new";
        si.addService(s2);
        
        assertEquals(1, si.getServices().size());
        assertEquals("new", ((ServiceIdentify.Service) si.getServices().elementAt(0)).password);
    }

    public void testServiceIdentifyNoticeHandling() {
        FakeSender sender = new FakeSender();
        Session session = new Session(server, sender);
        ServiceIdentify si = new ServiceIdentify(session);
        
        ServiceIdentify.Service ns = new ServiceIdentify.Service("NickServ");
        ns.password = "secret";
        si.addService(ns);
        
        assertTrue(si.handleNotice("NickServ", "You are now identified"));
        assertTrue(si.handleNotice("NickServ", "Password accepted"));
        assertFalse(si.handleNotice("NickServ", "Invalid password"));
        assertFalse(si.handleNotice("ChanServ", "You are identified"));
    }

    // ---- MessageHistory tests ----

    public void testMessageHistoryBasic() {
        MessageHistory mh = new MessageHistory();
        mh.add("first");
        mh.add("second");
        assertEquals(2, mh.size());
        assertEquals("second", mh.previous());
        assertEquals("first", mh.previous());
        assertEquals("first", mh.previous()); // stays at top
    }

    public void testMessageHistoryNext() {
        MessageHistory mh = new MessageHistory();
        mh.add("first");
        mh.add("second");
        mh.add("third");
        mh.previous(); // third
        mh.previous(); // second
        assertEquals("third", mh.next());
        assertEquals("third", mh.next()); // back to current
        assertNull(mh.next()); // at bottom
    }

    public void testMessageHistoryNoDuplicates() {
        MessageHistory mh = new MessageHistory();
        mh.add("same");
        mh.add("same");
        assertEquals(1, mh.size());
    }

    public void testMessageHistoryMaxSize() {
        MessageHistory mh = new MessageHistory(3);
        mh.add("1");
        mh.add("2");
        mh.add("3");
        mh.add("4");
        assertEquals(3, mh.size());
        assertTrue(mh.getAll().contains("4"));
        assertFalse(mh.getAll().contains("1"));
    }

}
