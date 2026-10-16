package com.chatterbox.core;

import java.util.Vector;

import com.chatterbox.crypto.E2E;
import com.chatterbox.proto.Ctcp;
import com.chatterbox.proto.IrcCodec;
import com.chatterbox.proto.IrcSanitizer;

/**
 * Slash-command dispatcher plus plain-text send path.
 *
 * Pure logic: it needs a {@link Sender} and a {@link ServerState}, nothing from
 * javax.microedition. The UI layer only has to render whatever the session
 * posts, so the whole command set is testable on desktop.
 *
 * Supported: /join /part /msg /me /nick /topic /quit /query /whois /clear
 *            plus /away /back /names /mode /kick /who /raw /help /close
 *            /server /cycle /e2e
 *
 * Assumptions:
 *  - A line that does not start with '/' is sent to the active window; for a
 *    channel that is PRIVMSG <#chan>, for a private window PRIVMSG <nick>.
 *    With pre-shared-key E2E enabled in Settings, the same paths get an
 *    encrypted "E2E:1:..." body instead of plaintext.
 *  - Unknown commands get an explicit error rather than being sent to the
 *    server, because a phone keyboard makes typos cheap and silent failure is
 *    the main way users give up on mobile clients.
 */
public final class CommandProcessor {

    /** Result of handling one input line, for the UI to react to. */
    public static final class Result {
        public boolean handled;
        /** The caller should switch the active window to this. */
        public ChannelState focus;
        /** The caller should clear the visible window. */
        public boolean clear;
        /** The caller should exit the MIDlet. */
        public boolean quit;
        /** User-facing text to show immediately. */
        public String notice = "";
        /**
         * The caller should switch to this theme kind (Theme.DARK/...).
         * -1 means "no theme change requested".
         */
        public int themeKind = -1;
    }

    private final ServerState server;
    private final Sender sender;
    private final Session session;

    public CommandProcessor(ServerState server, Session session, Sender sender) {
        this.server = server;
        this.session = session;
        this.sender = sender;
    }

    public Result execute(ChannelState active, String input) {
        Result r = new Result();
        String line = Str.trim(input);
        if (line.length() == 0) {
            return r;
        }
        if (line.charAt(0) != '/') {
            sendPlain(active, line, r);
            r.handled = true;
            return r;
        }

        Vector words = Str.words(line.substring(1));
        if (words.size() == 0) {
            return r;
        }
        String cmd = Str.lower((String) words.elementAt(0));
        String rest = line.substring(1 + cmd.length()).trim();

        if (cmd.equals("me") || cmd.equals("action")) {
            return doMe(active, rest, r);
        }
        if (cmd.equals("msg") || cmd.equals("m") || cmd.equals("say")) {
            return doMsg(active, rest, r);
        }
        if (cmd.equals("join") || cmd.equals("j")) {
            return doJoin(rest, r);
        }
        if (cmd.equals("part") || cmd.equals("leave")) {
            return doPart(active, rest, r);
        }
        if (cmd.equals("query") || cmd.equals("q")) {
            return doQuery(rest, r);
        }
        if (cmd.equals("nick")) {
            return doNick(rest, r);
        }
        if (cmd.equals("topic") || cmd.equals("t")) {
            return doTopic(active, rest, r);
        }
        if (cmd.equals("quit") || cmd.equals("q!")) {
            return doQuit(rest, r);
        }
        if (cmd.equals("whois")) {
            return doWhois(rest, r);
        }
        if (cmd.equals("who")) {
            sender.send(IrcCodec.who(rest.length() == 0 ? activeName(active) : rest));
            r.handled = true;
            return r;
        }
        if (cmd.equals("names")) {
            sender.send(IrcCodec.names(activeName(active)));
            r.handled = true;
            return r;
        }
        if (cmd.equals("mode")) {
            String target = rest.length() == 0 ? activeName(active) : rest;
            sender.send(IrcCodec.mode(target, null));
            r.handled = true;
            return r;
        }
        if (cmd.equals("kick")) {
            return doKick(active, rest, r);
        }
        if (cmd.equals("away")) {
            if (rest.length() == 0) {
                sender.send(prefixed("AWAY :"));
            } else {
                sender.send(prefixed("AWAY :" + rest));
            }
            r.handled = true;
            return r;
        }
        if (cmd.equals("back")) {
            sender.send(prefixed("AWAY"));
            r.handled = true;
            return r;
        }
        if (cmd.equals("clear")) {
            if (active != null) {
                active.getRing().clear();
            }
            r.clear = true;
            r.handled = true;
            return r;
        }
        if (cmd.equals("close")) {
            if (active != null && active.isPrivate()) {
                server.closeWindow(active.getName());
                r.handled = true;
                r.focus = null;
                r.notice = "closed";
                return r;
            }
            r.notice = "Cannot close a channel.";
            r.handled = true;
            return r;
        }
        if (cmd.equals("raw") || cmd.equals("quote")) {
            if (rest.length() > 0) {
                sender.send(rest.endsWith("\r\n") ? rest : rest + "\r\n");
                r.handled = true;
                return r;
            }
            r.notice = "Usage: /raw <line>";
            r.handled = true;
            return r;
        }
        if (cmd.equals("help") || cmd.equals("?")) {
            session.post(null, server.getHost(), Message.SERVER, HELP);
            r.handled = true;
            return r;
        }
        if (cmd.equals("cycle")) {
            return doCycle(active, r);
        }
        if (cmd.equals("ignore")) {
            return doIgnore(active, rest, r);
        }
        if (cmd.equals("unignore")) {
            return doUnignore(active, rest, r);
        }
        if (cmd.equals("highlights") || cmd.equals("highlight")) {
            return doHighlight(active, rest, r);
        }
        if (cmd.equals("service")) {
            return doService(active, rest, r);
        }
        if (cmd.equals("history")) {
            return doHistory(active, rest, r);
        }
        if (cmd.equals("e2e")) {
            return doE2e(rest, r);
        }
        if (cmd.equals("theme")) {
            return doTheme(rest, r);
        }

        r.notice = "Unknown command: /" + cmd;
        r.handled = true;
        return r;
    }

    /** Switch theme without leaving the chat. Persists via the MIDlet. */
    private Result doTheme(String rest, Result r) {
        r.handled = true;
        String name = Str.lower(Str.trim(rest));
        int kind = -1;
        String label = "";
        if (name.equals("dark")) {
            kind = Theme.DARK;
            label = "Dark";
        } else if (name.equals("light")) {
            kind = Theme.LIGHT;
            label = "Light";
        } else if (name.equals("high") || name.equals("hc")
                || name.equals("contrast") || name.equals("highcontrast")
                || name.equals("high contrast")) {
            kind = Theme.HIGH_CONTRAST;
            label = "High contrast";
        } else if (name.equals("amber")) {
            kind = Theme.AMBER;
            label = "Amber";
        }
        if (kind < 0) {
            r.notice = "Usage: /theme dark|light|high|amber";
        } else {
            r.themeKind = kind;
            r.notice = "Theme: " + label;
        }
        return r;
    }

    /** Show E2E status. The key itself lives in Settings, never on a wire. */
    private Result doE2e(String rest, Result r) {
        r.handled = true;
        if (rest.equals("on") || rest.equals("off")) {
            r.notice = "Use Settings > server profile to switch E2E "
                    + (rest.equals("on") ? "on" : "off") + ".";
            return r;
        }
        boolean ready = server.isE2eReady();
        boolean active;
        try {
            active = session.isE2eActive();
        } catch (Throwable t) {
            active = false;
        }
        r.notice = ready
                ? (active
                        ? "E2E is on (messages are encrypted)."
                          + " Key fingerprint: " + session.e2eFingerprint()
                          + " - peers must match."
                        : "E2E configured, key not ready.")
                : "E2E is off. Set an E2E key in Settings and send /e2e again.";
        return r;
    }

    private void sendPlain(ChannelState active, String text, Result r) {
        String target = activeName(active);
        if (target.length() == 0) {
            r.notice = "No active conversation. Try /join #channel";
            return;
        }
        if (!sendE2e(target, E2E.KIND_CHAT, text)) {
            String sanitized = IrcSanitizer.sanitizeMessage(text);
            sender.send(IrcCodec.privmsg(target, sanitized));
            // Echo locally: some servers do not echo, and on a slow link the user
            // needs to see what they typed immediately.
            Message m = new Message(System.currentTimeMillis(), Message.CHAT,
                    server.getSelfNick(), sanitized, target, true, false);
            if (active != null) {
                active.getRing().add(m);
            }
        }
    }

    /**
     * Encrypt and send one text when pre-shared-key E2E is active; otherwise
     * a no-op so the caller falls back to plaintext. Prints a local echo
     * (plaintext) into the window the way the plain path does.
     * @return true when the message was sent encrypted.
     */
    private boolean sendE2e(String target, byte kind, String text) {
        if (!session.isE2eActive()) {
            return false;
        }
        String clean = IrcSanitizer.sanitizeE2eMessage(text);
        String[] payloads = session.e2eEncode(target, kind, clean);
        if (payloads.length == 0) {
            return false;
        }
        for (int i = 0; i < payloads.length; i++) {
            sender.send(IrcCodec.privmsg(target, payloads[i]));
        }
        // Local echo in plaintext, flagged so the user can tell it is E2E.
        Message m = new Message(System.currentTimeMillis(), kind == E2E.KIND_ACTION ? Message.ACTION : Message.CHAT,
                server.getSelfNick(), "[E2E] " + clean, target, true, false);
        ChannelState win = server.window(target);
        if (win != null) {
            win.getRing().add(m);
        }
        return true;
    }

    private Result doMe(ChannelState active, String text, Result r) {
        r.handled = true;
        String target = activeName(active);
        if (text.length() == 0) {
            r.notice = "Usage: /me <action>";
            return r;
        }
        if (target.length() == 0) {
            r.notice = "No active conversation.";
            return r;
        }
        String sanitized = IrcSanitizer.sanitizeMessage(text);
        if (sendE2e(target, E2E.KIND_ACTION, text)) {
            return r;
        }
        sender.send(IrcCodec.action(target, sanitized));
        Message m = new Message(System.currentTimeMillis(), Message.ACTION,
                server.getSelfNick(), sanitized, target, true, false);
        if (active != null) {
            active.getRing().add(m);
        }
        return r;
    }

    private Result doMsg(ChannelState active, String rest, Result r) {
        r.handled = true;
        int sp = rest.indexOf(' ');
        String who = sp < 0 ? rest : rest.substring(0, sp);
        String text = sp < 0 ? "" : rest.substring(sp + 1).trim();
        who = IrcSanitizer.sanitizeNick(who);
        text = IrcSanitizer.sanitizeMessage(text);
        if (who.length() == 0 || text.length() == 0) {
            r.notice = "Usage: /msg <nick> <message>";
            return r;
        }
        if (!sendE2e(who, E2E.KIND_CHAT, text)) {
            sender.send(IrcCodec.privmsg(who, text));
        }
        ChannelState w = server.window(who, ChannelState.TYPE_PRIVATE);
        if (w != null) {
            Message m = new Message(System.currentTimeMillis(), Message.CHAT,
                    server.getSelfNick(), text, who, true, false);
            w.getRing().add(m);
            r.focus = w;
        }
        return r;
    }

    private Result doJoin(String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            r.notice = "Usage: /join #channel [key]";
            return r;
        }
        Vector v = Str.words(rest);
        String chan = IrcSanitizer.sanitizeChannel((String) v.elementAt(0));
        String key = v.size() > 1 ? IrcSanitizer.sanitizeParam((String) v.elementAt(1)) : null;
        if (key != null && key.length() > 0) {
            sender.send(IrcCodec.join(chan, key));
        } else {
            sender.send(IrcCodec.join(chan));
        }
        ChannelState c = server.window(chan, ChannelState.TYPE_CHANNEL);
        c.setParted(false);
        c.getRing().add(Message.server(System.currentTimeMillis(),
                "joining " + chan + "..."));
        r.focus = c;
        return r;
    }

    private Result doPart(ChannelState active, String rest, Result r) {
        r.handled = true;
        String chan = rest.length() > 0 ? IrcSanitizer.sanitizeChannel(rest) : activeName(active);
        if (chan.length() == 0) {
            r.notice = "Usage: /part <#channel>";
            return r;
        }
        sender.send(IrcCodec.part(chan, "bye"));
        ChannelState c = server.window(chan);
        if (c != null) {
            c.setParted(true);
        }
        return r;
    }

    private Result doQuery(String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            r.notice = "Usage: /query <nick>";
            return r;
        }
        String nick = IrcSanitizer.sanitizeNick(rest);
        ChannelState c = server.window(nick, ChannelState.TYPE_PRIVATE);
        r.focus = c;
        return r;
    }

    private Result doNick(String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            r.notice = "Usage: /nick <newnick>";
            return r;
        }
        String nick = IrcSanitizer.sanitizeNick(rest);
        sender.send(IrcCodec.nick(nick));
        return r;
    }

    private Result doTopic(ChannelState active, String rest, Result r) {
        r.handled = true;
        String chan = activeName(active);
        if (!Session.isChannelName(chan)) {
            r.notice = "/topic only works in a channel.";
            return r;
        }
        if (rest.length() == 0) {
            sender.send(IrcCodec.topic(chan, null));
            ChannelState c = server.window(chan);
            if (c != null) {
                session.post(c, c.getName(), Message.SERVER,
                        "Topic: " + (c.getTopic().length() == 0 ? "(none)" : c.getTopic()));
            }
            return r;
        }
        String topic = IrcSanitizer.sanitizeTrailing(rest);
        sender.send(IrcCodec.topic(chan, topic));
        return r;
    }

    private Result doQuit(String rest, Result r) {
        r.handled = true;
        r.quit = true;
        sender.send(IrcCodec.quit(rest.length() > 0 ? rest : "Chatterbox"));
        return r;
    }

    private Result doWhois(String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            r.notice = "Usage: /whois <nick>";
            return r;
        }
        String nick = IrcSanitizer.sanitizeNick(rest);
        sender.send(IrcCodec.whois(nick));
        return r;
    }

    private Result doKick(ChannelState active, String rest, Result r) {
        r.handled = true;
        String chan = activeName(active);
        if (!Session.isChannelName(chan)) {
            r.notice = "/kick only works in a channel.";
            return r;
        }
        Vector v = Str.words(rest);
        if (v.size() == 0) {
            r.notice = "Usage: /kick <nick> [reason]";
            return r;
        }
        String nick = IrcSanitizer.sanitizeNick((String) v.elementAt(0));
        String reason = v.size() > 1 ? IrcSanitizer.sanitizeTrailing(
                rest.substring(rest.indexOf((String) v.elementAt(0)) + ((String) v.elementAt(0)).length()).trim())
                : "";
        sender.send(IrcCodec.kick(chan, nick, reason));
        return r;
    }

    private Result doCycle(ChannelState active, Result r) {
        r.handled = true;
        String chan = activeName(active);
        if (!Session.isChannelName(chan)) {
            r.notice = "/cycle only works in a channel.";
            return r;
        }
        sender.send(IrcCodec.part(chan, "cycling"));
        sender.send(IrcCodec.join(IrcSanitizer.sanitizeChannel(chan)));
        ChannelState c = server.window(chan);
        if (c != null) {
            c.getRing().add(Message.server(System.currentTimeMillis(), "cycling " + chan));
        }
        return r;
    }

    private String activeName(ChannelState active) {
        if (active == null) {
            return "";
        }
        return active.getName();
    }

    private String prefixed(String line) {
        String nick = server.getSelfNick();
        if (nick == null || nick.length() == 0) {
            nick = "chatterbox";
        }
        return ":" + nick + " " + line + "\r\n";
    }

    private static final String HELP =
            "/join #ch  /part [#ch]  /msg nick text  /me action  /nick newnick"
            + "  /topic text  /query nick  /whois nick  /who  /names  /mode"
            + "  /kick nick  /away [msg]  /back  /cycle  /clear  /close"
            + "  /raw line  /e2e  /theme name  /help";

    /** CTCP ACTION helper exposed for the UI's "/me" menu item. */
    public static String actionLine(String target, String text) {
        return IrcCodec.privmsg(target, Ctcp.wrap(Ctcp.ACTION, text));
    }

    private Result doIgnore(ChannelState active, String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            // Show ignore list
            java.util.Vector entries = session.getIgnoreList().getEntries();
            StringBuilder sb = new StringBuilder();
            sb.append("Ignore list (").append(entries.size()).append("):\n");
            for (int i = 0; i < entries.size(); i++) {
                IgnoreList.Entry e = (IgnoreList.Entry) entries.elementAt(i);
                String type = e.matchHost ? "host" : "nick";
                sb.append("  [").append(type).append("] ").append(e.pattern).append("\n");
            }
            r.notice = sb.toString();
            return r;
        }
        // Add to ignore list
        java.util.Vector v = Str.words(rest);
        String[] parts = new String[v.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = (String) v.elementAt(i);
        String pattern = parts[0];
        boolean host = false;
        if (parts.length > 1 && ("host".equalsIgnoreCase(parts[1]) || "mask".equalsIgnoreCase(parts[1]))) {
            host = true;
        }
        if (host && pattern.indexOf('@') < 0) {
            pattern = "*@" + pattern;
        }
        session.getIgnoreList().add(pattern);
        r.notice = "Ignoring: " + pattern;
        return r;
    }

    private Result doUnignore(ChannelState active, String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            r.notice = "Usage: /unignore <pattern>";
            return r;
        }
        session.getIgnoreList().remove(rest);
        r.notice = "Removed from ignore list: " + rest;
        return r;
    }

    private Result doHighlight(ChannelState active, String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            // Show highlights
            java.util.Vector list = session.getHighlightRules().getRules();
            StringBuilder sb = new StringBuilder();
            sb.append("Highlight rules (").append(list.size()).append("):\n");
            for (int i = 0; i < list.size(); i++) {
                HighlightRules.Rule hr = (HighlightRules.Rule) list.elementAt(i);
                sb.append("  ").append(hr.pattern);
                if (hr.wholeWord) sb.append(" [word]");
                if (hr.caseSensitive) sb.append(" [case]");
                if (hr.matchHost) sb.append(" [host]");
                sb.append("\n");
            }
            r.notice = sb.toString();
            return r;
        }
        // Add highlight rule
        java.util.Vector v = Str.words(rest);
        String[] parts = new String[v.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = (String) v.elementAt(i);
        String pattern = parts[0];
        boolean word = true, cases = false, host = false;
        for (int i = 1; i < parts.length; i++) {
            if ("word".equalsIgnoreCase(parts[i])) word = true;
            else if ("noword".equalsIgnoreCase(parts[i])) word = false;
            else if ("case".equalsIgnoreCase(parts[i])) cases = true;
            else if ("host".equalsIgnoreCase(parts[i])) host = true;
        }
        HighlightRules.Rule hr = new HighlightRules.Rule(pattern);
        hr.wholeWord = word;
        hr.caseSensitive = cases;
        hr.matchHost = host;
        session.getHighlightRules().add(hr);
        r.notice = "Highlight added: " + pattern;
        return r;
    }

    private Result doService(ChannelState active, String rest, Result r) {
        r.handled = true;
        if (rest.length() == 0) {
            // Show services
            java.util.Vector list = session.getServiceIdentify().getServices();
            StringBuilder sb = new StringBuilder();
            sb.append("Services (").append(list.size()).append("):\n");
            for (int i = 0; i < list.size(); i++) {
                ServiceIdentify.Service s = (ServiceIdentify.Service) list.elementAt(i);
                String status = s.enabled ? "on" : "off";
                sb.append("  ").append(s.name).append(" (").append(status).append(")").append("\n");
            }
            r.notice = sb.toString();
            return r;
        }
        // Add/edit service
        java.util.Vector v = Str.words(rest);
        String[] parts = new String[v.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = (String) v.elementAt(i);
        if (parts.length < 2) {
            r.notice = "Usage: /service <name> <password> [account]";
            return r;
        }
        String name = parts[0];
        String pass = parts[1];
        String acct = parts.length > 2 ? parts[2] : "";
        ServiceIdentify.Service s = new ServiceIdentify.Service(name);
        s.password = pass;
        s.account = acct;
        session.getServiceIdentify().addService(s);
        r.notice = "Service added: " + name;
        return r;
    }

    private Result doHistory(ChannelState active, String rest, Result r) {
        r.handled = true;
        MessageHistory hist = session.getMessageHistory();
        if (rest.equals("clear")) {
            hist.clear();
            r.notice = "History cleared";
            return r;
        }
        java.util.Vector msgs = hist.getAll();
        StringBuilder sb = new StringBuilder();
        sb.append("History (").append(msgs.size()).append("):\n");
        for (int i = 0; i < msgs.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append((String) msgs.elementAt(i)).append("\n");
        }
        r.notice = sb.toString();
        return r;
    }

}
