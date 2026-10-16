package com.chatterbox.core;

import java.util.Vector;

import com.chatterbox.proto.Ctcp;
import com.chatterbox.proto.IrcCodec;
import com.chatterbox.proto.IrcLine;
import com.chatterbox.proto.IrcParser;
import com.chatterbox.proto.Numeric;
import com.chatterbox.core.IgnoreList;
import com.chatterbox.core.HighlightRules;
import com.chatterbox.core.ServiceIdentify;
import com.chatterbox.core.MessageHistory;
import com.chatterbox.core.WhoisCache;
import com.chatterbox.core.AutoAway;
import com.chatterbox.proto.Cap;
import com.chatterbox.crypto.E2E;

/**
 * Protocol state machine for one server: consumes parsed {@link IrcLine}s,
 * mutates {@link ServerState}, and raises {@link IrcEventListener} callbacks.
 *
 * Threading: every public entry point is synchronized on the Session instance.
 * The reader thread is the only caller in production; tests call feedLine()
 * directly from the test thread. Holding one lock for the whole dispatch keeps
 * NAMES/353 sequences consistent with what the reader thread observed, and the
 * critical sections are microseconds.
 *
 * Assumptions:
 *  - We trust the caller for framing (LineReader does the 512-byte limit).
 *  - Unknown commands and unknown numerics are ignored rather than treated as
 *    errors; the IRC server command set is large and growing.
 *  - CTCP ACTION inside a channel also creates the window if we somehow missed
 *    the JOIN (happens after a reconnect race).
 */
public final class Session {

    public static final String VERSION_STRING = "Chatterbox 1.0 (Java ME)";

    private final ServerState server;
    private final Sender sender;
    public Sender getSender() { return sender; }
    private IrcEventListener listener;

    /** True once 001 arrives; gates auto-join and queue replay. */
    private boolean registered;
    /** Channels we asked to join, replayed after every reconnect. */
    private final Vector autoJoin = new Vector();
    private final Vector channelKeys = new Vector();

    private long pingSentAt;
    private String pendingPing;
    private int nickCollisions;
    private WhoisCache whoisCache;
    private AutoAway autoAway;
    private IgnoreList ignoreList;
    private HighlightRules highlightRules;
    private ServiceIdentify serviceIdentify;
    private MessageHistory messageHistory;
    private boolean capsDone;
    private String[] ackedCaps = new String[0];

    // Pre-shared-key E2E cache. Rebuilt lazily when the configured
    // passphrase changes; cleared when the passphrase is removed.
    private E2E e2eCipher;
    private String e2eKeySeed;
    private byte[] e2eKeyBytes;

    public Session(ServerState server, Sender sender) {
        this.server = server;
        this.sender = sender;
        reset();
    }

    public ServerState getServer() {
        return server;
    }

    public void setListener(IrcEventListener l) {
        this.listener = l;
    }

    public boolean isRegistered() {
        return registered;
    }

    public IgnoreList getIgnoreList() {
        return ignoreList;
    }

    public HighlightRules getHighlightRules() {
        return highlightRules;
    }

    public ServiceIdentify getServiceIdentify() {
        return serviceIdentify;
    }

    public MessageHistory getMessageHistory() {
        return messageHistory;
    }

    public synchronized void reset() {
        registered = false;
        pingSentAt = 0;
        pendingPing = null;
        nickCollisions = 0;
        whoisCache = new WhoisCache();
        ignoreList = new IgnoreList();
        highlightRules = new HighlightRules();
        serviceIdentify = new ServiceIdentify(this);
        messageHistory = new MessageHistory();
        autoAway = new AutoAway(this, new AutoAway.AwayListener() {
            public void onAutoAway(String msg) {
                post(null, server.getHost(), 10, "Auto-away: " + msg);
            }
            public void onAutoBack() {
                post(null, server.getHost(), 10, "Auto-back");
            }
        });
        capsDone = false;
        ackedCaps = new String[0];
        // E2E cache from the previous incarnation must not leak into this one.
        e2eKeySeed = null;
        e2eKeyBytes = null;
        e2eCipher = null;
    }

    // ------------------------------------------------------------------
    // Registration / handshake
    // ------------------------------------------------------------------

    public void register() {
        registered = false;
        capsDone = false;
        ackedCaps = new String[0];
        server.setState(ServerState.REGISTERING);
        String pw = server.getPassword();
        if (pw != null && pw.length() > 0) {
            sender.send(IrcCodec.pass(pw));
        }
        String n = pickNick();
        sender.send(IrcCodec.nick(n));
        sender.send(IrcCodec.user(safeIdent(n), "0", "*",
                server.getRealName().length() > 0 ? server.getRealName() : n));
        // Request CAPs early (before registration completes)
        sender.send(com.chatterbox.proto.Cap.reqAll());
    }

    private String pickNick() {
        String n = server.getNick();
        if (n == null || n.length() == 0) {
            n = "chatterbox";
        }
        // Strip characters no ircd accepts in a nick.
        StringBuffer sb = new StringBuffer(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_'
                    || c == '[' || c == ']' || c == '\\' || c == '`' || c == '^' || c == '{' || c == '}';
            if (ok) {
                sb.append(c);
            }
        }
        String out = sb.toString();
        if (out.length() == 0) {
            out = "chatterbox";
        }
        if (out.length() > 30) {
            out = out.substring(0, 30);
        }
        if (out.charAt(0) >= '0' && out.charAt(0) <= '9') {
            out = "_" + out;
        }
        return out;
    }

    /** IRC ident: alphanumerics and a few punctuation marks only. */
    private String safeIdent(String nick) {
        StringBuffer sb = new StringBuffer(nick.length());
        for (int i = 0; i < nick.length(); i++) {
            char c = nick.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-') {
                sb.append(c);
            }
        }
        String s = sb.toString();
        if (s.length() == 0 || s.charAt(0) >= '0' && s.charAt(0) <= '9') {
            s = "cb" + s;
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Inbound dispatch
    // ------------------------------------------------------------------

    /** Convenience for tests and for the gateway-mirror debug view. */
    public void feedLine(String line) {
        feed(IrcParser.parse(line));
    }

    public synchronized void feed(IrcLine in) {
        if (in == null) {
            return;
        }
        String cmd = in.getCommand();
        if (cmd == null || cmd.length() == 0) {
            return;
        }

        if (IrcParser.isNumeric(cmd)) {
            handleNumeric(in, cmd);
            return;
        }

        if (cmd.equals("PING")) {
            handlePing(in);
            return;
        }
        if (cmd.equals("PONG")) {
            handlePong(in);
            return;
        }
        if (cmd.equals("PRIVMSG")) {
            handlePrivmsg(in, Message.CHAT, false);
            return;
        }
        if (cmd.equals("NOTICE")) {
            handlePrivmsg(in, Message.NOTICE, true);
            return;
        }
        if (cmd.equals("JOIN")) {
            handleJoin(in);
            return;
        }
        if (cmd.equals("PART")) {
            handlePart(in);
            return;
        }
        if (cmd.equals("QUIT")) {
            handleQuit(in);
            return;
        }
        if (cmd.equals("NICK")) {
            handleNick(in);
            return;
        }
        if (cmd.equals("KICK")) {
            handleKick(in);
            return;
        }
        if (cmd.equals("MODE")) {
            handleMode(in);
            return;
        }
        if (cmd.equals("TOPIC")) {
            handleTopic(in);
            return;
        }
        if (cmd.equals("CAP")) {
            handleCap(in);
            return;
        }
        if (cmd.equals("INVITE")) {
            handleInvite(in);
            return;
        }
        if (cmd.equals("ERROR")) {
            handleError(in);
            return;
        }
        // CAP, ACCOUNT, CHGHOST, WALLOPS, etc: deliberately ignored.
    }

    private void handlePing(IrcLine in) {
        String token = in.getTrailing();
        if (token == null) {
            token = in.getParam(0);
        }
        if (token == null) {
            token = server.getHost();
        }
        sender.send(IrcCodec.pong(token));
    }

    private void handlePong(IrcLine in) {
        if (pingSentAt > 0) {
            long ms = System.currentTimeMillis() - pingSentAt;
            pingSentAt = 0;
            server.setLagMs(ms);
            if (listener != null) {
                listener.onLag(server, ms);
            }
        }
    }

    /** Called by the network layer's keepalive timer. */
    public synchronized void sendKeepalivePing() {
        if (pingSentAt != 0) {
            // Previous PING never came back; the socket is probably dead.
            return;
        }
        pingSentAt = System.currentTimeMillis();
        pendingPing = "cb" + pingSentAt;
        sender.send(IrcCodec.ping(pendingPing));
    }

    private void handlePrivmsg(IrcLine in, int kind, boolean notice) {
        String target = in.getParam(0);
        String text = in.getTrailing();
        if (target == null || text == null) {
            return;
        }
        String from = in.getPrefixNick();
        if (from == null) {
            return;
        }
        boolean isSelf = IrcCase.same(from, server.getSelfNick());

        // CTCP handling.
        if (Ctcp.isCtcp(text)) {
            String tag = Ctcp.tag(text);
            if (Ctcp.ACTION.equals(tag)) {
                deliver(in, target, from, Ctcp.body(text), Message.ACTION, notice, isSelf);
                return;
            }
            if (Ctcp.VERSION.equals(tag)) {
                if (!notice) {
                    sender.send(IrcCodec.versionReply(from, VERSION_STRING));
                }
                return;
            }
            if (Ctcp.PING.equals(tag)) {
                sender.send(IrcCodec.notice(from, Ctcp.wrap("PONG", Ctcp.body(text))));
                return;
            }
            // Unknown CTCP: ignore entirely, never echo to the window.
            return;
        }

        // E2E payload: decrypt before anything else touches the text.
        if (E2E.isE2e(text)) {
            byte[] key = e2eKey();
            if (key == null) {
                deliver(in, target, from,
                        "[E2E] encrypted message (no key set - see Settings)", kind, notice, isSelf);
                return;
            }
            E2E.Plain p = E2E.decode(key, from, target, text);
            if (p == null) {
                deliver(in, target, from,
                        "[E2E] encrypted message could not be decrypted", kind, notice, isSelf);
                return;
            }
            deliver(in, target, from, p.text,
                    p.kind == E2E.KIND_ACTION ? Message.ACTION : kind, notice, isSelf);
            return;
        }

        // A notice addressed to us is personal; to a channel it is a window msg.
        deliver(in, target, from, text, kind, notice, isSelf);
    }

    // ------------------------------------------------------------------
    // Prep-shared-key (PSK) E2E encryption
    // ------------------------------------------------------------------

    /** Traffic key for E2E, or null when no passphrase is configured. */
    private synchronized byte[] e2eKey() {
        String k = server.getE2eKey();
        if (k == null || k.length() == 0) {
            e2eKeyBytes = null;
            e2eCipher = null;
            e2eKeySeed = null;
            return null;
        }
        if (!k.equals(e2eKeySeed)) {
            e2eKeyBytes = E2E.deriveKey(k);
            e2eCipher = new E2E(k, server.getSelfNick());
            e2eKeySeed = k;
        }
        return e2eKeyBytes;
    }

    /** True when outgoing text should be encrypted. */
    public synchronized boolean isE2eActive() {
        return server.isE2eReady() && e2eKey() != null;
    }

    /**
     * Encrypt one outbound text for a target. Returns the PRIVMSG trailing
     * payload(s) to send; empty when E2E is off.
     */
    public synchronized String[] e2eEncode(String target, byte kind, String text) {
        if (!server.isE2eReady() || e2eKey() == null || e2eCipher == null) {
            return new String[0];
        }
        return e2eCipher.encode(target, kind, text);
    }

    private void deliver(IrcLine in, String target, String from, String text,
                         int kind, boolean notice, boolean isSelf) {
        // Check ignore list
        if (ignoreList != null && ignoreList.isIgnored(from, in.getPrefixUser(), in.getPrefixHost())) {
            return; // silently drop
        }
        String me = server.getSelfNick();
        boolean targetIsChannel = isChannelName(target) && !IrcCase.same(target, me);

        ChannelState win;
        if (targetIsChannel) {
            // Channel message: we may not be in the channel yet (lazy join, or a
            // +n network we were only visible to). Buffer it anyway.
            win = server.window(target, ChannelState.TYPE_CHANNEL);
        } else {
            // Addressed to us, or a direct nick message: private window.
            boolean isNew = server.window(from) == null;
            win = server.window(from, ChannelState.TYPE_PRIVATE);
            if (isNew && listener != null) {
                listener.onWindowOpened(server, win);
            }
        }
        if (win == null) {
            return;
        }
        win.setPeerNick(from);

        boolean isPrivate = win.isPrivate();
        boolean highlight = false;
        if (!isSelf && me.length() > 0 && !notice) {
            highlight = Str.containsIgnoreCase(text, me);
        }
        if (isPrivate && !isSelf) {
            highlight = true;
        }

        Message m = new Message(System.currentTimeMillis(), kind, from, text,
                win.getName(), isSelf, highlight);
        win.getRing().add(m);
        win.markUnread(highlight);
        win.touch();

        if (listener != null) {
            listener.onMessage(server, win, m);
            if (highlight) {
                listener.onAlert(server, win, !isPrivate, isPrivate);
            }
        }
    }

    private void handleJoin(IrcLine in) {
        String who = in.getPrefixNick();
        String chan = in.getParam(0);
        if (who == null || chan == null) {
            return;
        }
        boolean isSelf = IrcCase.same(who, server.getSelfNick());
        if (isSelf) {
            server.setSelfNick(who);
            server.setSelfUser(in.getPrefixUser());
            server.setSelfHost(in.getPrefixHost());
        }
        ChannelState c = server.window(chan, ChannelState.TYPE_CHANNEL);
        if (c == null) {
            return;
        }
        UserEntry u = c.user(who, true);
        if (u != null && isSelf) {
            u.setSelf(true);
            u.setIdent(in.getPrefixUser(), in.getPrefixHost(), "");
        }
        // Our own JOIN/PART are noise: the window title already says where we
        // are, and on a 12-row screen they cost more than they inform.
        if (!isSelf) {
            Message m = Message.event(System.currentTimeMillis(), Message.JOIN, who, "joined");
            m.target = c.getName();
            c.getRing().add(m);
            c.touch();
            if (listener != null) {
                listener.onMessage(server, c, m);
            }
        }
        c.touch();
    }

    private void handlePart(IrcLine in) {
        String who = in.getPrefixNick();
        String chan = in.getParam(0);
        if (who == null || chan == null) {
            return;
        }
        ChannelState c = server.window(chan);
        if (c == null) {
            return;
        }
        boolean isSelf = IrcCase.same(who, server.getSelfNick());
        String reason = in.getTrailing();
        if (!isSelf) {
            Message m = Message.event(System.currentTimeMillis(), Message.PART, who,
                    reason == null ? "left" : "left (" + reason + ")");
            m.target = c.getName();
            c.getRing().add(m);
            c.touch();
            if (listener != null) {
                listener.onMessage(server, c, m);
            }
        }
        if (isSelf) {
            c.setParted(true);
            c.user(who, false);
            removeAutoJoin(chan);
            if (listener != null) {
                listener.onWindowClosed(server, c);
            }
        } else {
            c.removeUser(who);
        }
    }

    private void handleQuit(IrcLine in) {
        String who = in.getPrefixNick();
        if (who == null) {
            return;
        }
        String reason = in.getTrailing();
        Vector names = server.windowNames();
        for (int i = 0; i < names.size(); i++) {
            ChannelState c = server.window((String) names.elementAt(i));
            if (c == null || !c.isChannel()) {
                continue;
            }
            if (c.userCount() == 0 || c.findUser(who) == null) {
                continue;
            }
            c.removeUser(who);
            Message m = Message.event(System.currentTimeMillis(), Message.QUIT, who,
                    "quit" + (reason == null ? "" : " (" + reason + ")"));
            m.target = c.getName();
            c.getRing().add(m);
            if (listener != null) {
                listener.onMessage(server, c, m);
            }
        }
    }

    private void handleNick(IrcLine in) {
        String from = in.getPrefixNick();
        String to = in.getParam(0);
        if (from == null || to == null) {
            return;
        }
        boolean isSelf = IrcCase.same(from, server.getSelfNick());
        Vector names = server.windowNames();
        for (int i = 0; i < names.size(); i++) {
            ChannelState c = server.window((String) names.elementAt(i));
            if (c == null) {
                continue;
            }
            c.renameUser(from, to);
            if (c.isPrivate()) {
                continue;
            }
            Message m = Message.event(System.currentTimeMillis(), Message.NICK, from,
                    "is now known as " + to);
            m.target = c.getName();
            c.getRing().add(m);
            if (listener != null) {
                listener.onMessage(server, c, m);
            }
        }
        if (isSelf) {
            server.setSelfNick(to);
            server.setNick(to);
        }
    }

    private void handleKick(IrcLine in) {
        String chan = in.getParam(0);
        String victim = in.getParam(1);
        if (chan == null || victim == null) {
            return;
        }
        ChannelState c = server.window(chan);
        if (c == null) {
            return;
        }
        String reason = in.getTrailing();
        Message m = Message.event(System.currentTimeMillis(), Message.KICK, victim,
                "was kicked by " + Str.nvl(in.getPrefixNick())
                        + (reason == null ? "" : " (" + reason + ")"));
        m.target = c.getName();
        c.getRing().add(m);
        if (listener != null) {
            listener.onMessage(server, c, m);
        }
        if (IrcCase.same(victim, server.getSelfNick())) {
            c.setParted(true);
            c.user(victim, false);
            removeAutoJoin(chan);
            post(c, "", Message.SERVER, "You were kicked from " + chan);
            if (listener != null) {
                listener.onWindowClosed(server, c);
            }
        } else {
            c.removeUser(victim);
        }
    }

    private void handleMode(IrcLine in) {
        String target = in.getParam(0);
        if (target == null) {
            return;
        }
        // Channel mode: :op MODE #chan +o nick   |   :op MODE #chan +nt
        if (isChannelName(target)) {
            ChannelState c = server.window(target);
            String modes = in.getParam(1);
            if (modes == null || modes.length() == 0) {
                return;
            }
            String arg = in.getParam(2);
            char first = modes.charAt(0);
            String payload = modes;
            if (first == '+' || first == '-') {
                if (arg != null && arg.length() > 0) {
                    payload = modes.substring(1) + " " + arg;
                } else {
                    payload = modes.length() > 1 ? modes.substring(1) : "";
                }
            }
            Message m = Message.event(System.currentTimeMillis(), Message.MODE,
                    in.getPrefixNick(), "sets mode " + payload);
            m.target = target;
            if (c != null) {
                c.setModes(modes);
                c.getRing().add(m);
                if (listener != null) {
                    listener.onMessage(server, c, m);
                }
                // Apply prefix modes to the target user if an argument was given.
                if (arg != null && arg.length() > 0) {
                    c.applyPrefixMode(arg, modes);
                }
            }
            return;
        }
        // User mode on ourselves: :me MODE nicknet :+i
        Message m = Message.event(System.currentTimeMillis(), Message.MODE,
                in.getPrefixNick(), Str.nvl(in.getTrailing()));
        post(null, target, Message.MODE, m.nick + " " + m.text);
    }

    private void handleTopic(IrcLine in) {
        String chan = in.getParam(0);
        String topic = in.getTrailing();
        if (chan == null) {
            return;
        }
        ChannelState c = server.window(chan, ChannelState.TYPE_CHANNEL);
        if (c == null) {
            return;
        }
        c.setTopic(topic == null ? "" : topic, in.getPrefixNick(), System.currentTimeMillis());
        Message m = Message.event(System.currentTimeMillis(), Message.TOPIC,
                in.getPrefixNick(), "set the topic to " + Str.clip(topic, 80));
        m.target = chan;
        c.getRing().add(m);
        if (listener != null) {
            listener.onMessage(server, c, m);
            listener.onTopicChanged(server, c, c.getTopic());
        }
    }

    private void handleCap(IrcLine in) {
        String sub = Cap.parseSubcommand(in);
        if (sub == null) {
            return;
        }
        if (sub.equals(Cap.ACK)) {
            ackedCaps = Cap.parseAckCaps(in);
        } else if (sub.equals(Cap.NAK)) {
            // Cap not supported, ignore
        } else if (sub.equals(Cap.LIST)) {
            // Server lists available caps - we could re-request but we already REQ'd
        } else if (sub.equals(Cap.END)) {
            capsDone = true;
            // If we haven't registered yet, continue
            if (!registered) {
                sender.send(Cap.end());
            }
        } else if (sub.equals(Cap.NEW)) {
            // New cap available - could auto-request
        } else if (sub.equals(Cap.DEL)) {
            // Cap removed
        }
    }

    private void handleInvite(IrcLine in) {
        String nick = in.getParam(1);
        String chan = in.getTrailing();
        String who = Str.nvl(in.getPrefixNick());
        if (nick == null) {
            return;
        }
        ChannelState c = server.window("#invite", ChannelState.TYPE_STATUS);
        Message m = Message.server(System.currentTimeMillis(),
                who + " invites you to " + Str.nvl(chan));
        c.getRing().add(m);
        if (listener != null) {
            listener.onMessage(server, c, m);
        }
    }

    private void handleError(IrcLine in) {
        String reason = Str.nvl(in.getTrailing());
        post(null, server.getHost(), Message.SERVER, "*** " + reason);
        if (listener != null) {
            listener.onError(server, reason);
        }
    }

    // ------------------------------------------------------------------
    // Numerics
    // ------------------------------------------------------------------

    private void handleNumeric(IrcLine in, String cmd) {
        String target = in.getParam(0);

        if (cmd.equals(Numeric.RPL_WELCOME)) {
            registered = true;
            server.setSelfNick(target == null ? server.getSelfNick() : target);
            server.setNick(server.getSelfNick());
            server.setState(ServerState.CONNECTED);
            post(null, server.getHost(), Message.SERVER, Str.nvl(in.getTrailing()));
            replayAutoJoin();
            serviceIdentify.identifyAll(server.getSelfNick());
            if (listener != null) {
                listener.onStateChanged(server, ServerState.CONNECTED, "connected");
            }
            return;
        }

        if (cmd.equals(Numeric.RPL_ISUPPORT)) {
            post(null, server.getHost(), Message.SERVER, Str.nvl(in.getTrailing()));
            return;
        }

        if (cmd.equals(Numeric.RPL_NAMREPLY)) {
            // :server 353 mynick = #chan :@op +vo other
            String chan = in.getParam(2);
            String list = in.getTrailing();
            if (chan == null || list == null) {
                return;
            }
            ChannelState c = server.window(chan, ChannelState.TYPE_CHANNEL);
            applyNames(c, list);
            return;
        }

        if (cmd.equals(Numeric.RPL_ENDOFNAMES)) {
            String chan = in.getParam(1);
            ChannelState c = server.window(chan);
            if (c != null) {
                // Mark ourselves and seed a synthetic topic line if we have one.
                UserEntry u = c.user(server.getSelfNick(), true);
                if (u != null) {
                    u.setSelf(true);
                }
                post(server.window(chanNameOf(c)), c.getName(), Message.JOIN,
                        "-- " + c.userCount() + " users --");
            }
            return;
        }

        if (cmd.equals(Numeric.RPL_TOPIC)) {
            String chan = in.getParam(1);
            String topic = in.getTrailing();
            if (chan == null) {
                return;
            }
            ChannelState c = server.window(chan, ChannelState.TYPE_CHANNEL);
            if (c != null) {
                c.setTopic(Str.nvl(topic), "", 0);
                Message m = Message.event(System.currentTimeMillis(), Message.TOPIC, null,
                        "Topic: " + Str.nvl(topic));
                m.target = chan;
                c.getRing().add(m);
                if (listener != null) {
                    listener.onMessage(server, c, m);
                    listener.onTopicChanged(server, c, c.getTopic());
                }
            }
            return;
        }

        if (cmd.equals(Numeric.RPL_NOTOPIC)) {
            String chan = in.getParam(1);
            ChannelState c = server.window(chan);
            if (c != null) {
                c.setTopic("", "", 0);
            }
            return;
        }

        if (cmd.equals(Numeric.ERR_NICKNAMEINUSE)) {
            nickCollisions++;
            String attempted = server.getNick();
            String chosen = nextNick(attempted, nickCollisions);
            server.setNick(chosen);
            server.setSelfNick(chosen);
            if (listener != null) {
                listener.onNickInUse(server, attempted, chosen);
            }
            post(null, server.getHost(), Message.SERVER,
                    "Nick '" + attempted + "' is in use, trying '" + chosen + "'");
            sender.send(IrcCodec.nick(chosen));
            return;
        }

        if (cmd.equals(Numeric.ERR_PASSWDMISMATCH)
                || cmd.equals(Numeric.ERR_YOUREBANNEDCREEP)) {
            String reason = cmd.equals(Numeric.ERR_PASSWDMISMATCH)
                    ? "Incorrect password" : "You are banned from this server";
            post(null, server.getHost(), Message.SERVER, "*** " + reason);
            if (listener != null) {
                listener.onError(server, reason);
            }
            return;
        }

        if (cmd.equals(Numeric.ERR_NOSUCHNICK) || cmd.equals(Numeric.ERR_NOSUCHSERVER)) {
            post(null, server.getHost(), Message.SERVER,
                    "*** No such nick/channel: " + Str.nvl(target));
            return;
        }

        if (cmd.equals(Numeric.ERR_NOSUCHCHANNEL) || cmd.equals(Numeric.ERR_CANNOTSENDTOCHAN)) {
            String chan = in.getParam(1);
            ChannelState c = server.window(chan);
            if (c != null) {
                c.getRing().add(Message.server(System.currentTimeMillis(),
                        "*** " + cmd + ": cannot send to " + chan));
                c.setParted(true);
            }
            post(null, server.getHost(), Message.SERVER,
                    "*** Cannot send to channel " + Str.nvl(chan));
            return;
        }

        if (cmd.equals(Numeric.RPL_CHANNELMODEIS)) {
            String chan = in.getParam(1);
            String modes = in.getParam(2);
            ChannelState c = server.window(chan);
            if (c != null) {
                c.setModes(Str.nvl(modes));
            }
            return;
        }

        if (Numeric.isError(cmd)) {
            post(null, server.getHost(), Message.SERVER,
                    "*** " + cmd + ": " + Str.nvl(in.getTrailing()));
            return;
        }

        // Any other numeric still belongs on screen so the user sees MOTD etc.
        if (in.getTrailing() != null && in.getTrailing().length() > 0) {
            post(null, server.getHost(), Message.SERVER, in.getTrailing());
        }
    }

    /** "@+ov...nick" -> prefix "@" plus the bare nick. */
    private void applyNames(ChannelState channel, String list) {
        if (channel == null || list == null) {
            return;
        }
        Vector toks = Str.words(list);
        for (int i = 0; i < toks.size(); i++) {
            String tok = (String) toks.elementAt(i);
            String prefix = "";
            while (tok.length() > 0 && isPrefixChar(tok.charAt(0))) {
                prefix += tok.charAt(0);
                tok = tok.substring(1);
            }
            if (tok.length() == 0) {
                continue;
            }
            UserEntry u = channel.user(tok, true);
            u.setPrefix(prefix);
            u.setSelf(IrcCase.same(tok, server.getSelfNick()));
        }
    }

    private static boolean isPrefixChar(char c) {
        return c == '@' || c == '+' || c == '%' || c == '&' || c == '~' || c == '!';
    }

    private String nextNick(String base, int attempt) {
        String alt = server.getAltNick();
        if (attempt == 1 && alt != null && alt.length() > 0 && !IrcCase.same(alt, base)) {
            return alt;
        }
        String suffix = String.valueOf(attempt);
        int max = 30 - suffix.length();
        String b = base.length() > max ? base.substring(0, max) : base;
        return b + suffix;
    }

    // ------------------------------------------------------------------
    // Auto-join bookkeeping
    // ------------------------------------------------------------------

    public synchronized void setAutoJoin(String csv) {
        autoJoin.removeAllElements();
        channelKeys.removeAllElements();
        if (csv == null) {
            return;
        }
        Vector v = Str.split(csv, ',');
        for (int i = 0; i < v.size(); i++) {
            String s = Str.trim((String) v.elementAt(i));
            if (s.length() == 0) {
                continue;
            }
            // Accept "#chan key" or "#chan,key" style.
            int sp = s.indexOf(' ');
            String chan = sp < 0 ? s : s.substring(0, sp);
            String key = sp < 0 ? "" : Str.trim(s.substring(sp + 1));
            if (!isChannelName(chan)) {
                continue;
            }
            autoJoin.addElement(chan);
            channelKeys.addElement(key);
        }
    }

    public synchronized Vector getAutoJoin() {
        Vector v = new Vector(autoJoin.size());
        for (int i = 0; i < autoJoin.size(); i++) {
            v.addElement(autoJoin.elementAt(i));
        }
        return v;
    }

    private synchronized void removeAutoJoin(String chan) {
        for (int i = 0; i < autoJoin.size(); i++) {
            if (IrcCase.same((String) autoJoin.elementAt(i), chan)) {
                autoJoin.removeElementAt(i);
                channelKeys.removeElementAt(i);
                return;
            }
        }
    }

    public synchronized void replayAutoJoin() {
        for (int i = 0; i < autoJoin.size(); i++) {
            String chan = (String) autoJoin.elementAt(i);
            String key = (String) channelKeys.elementAt(i);
            if (key != null && key.length() > 0) {
                sender.send(IrcCodec.join(chan, key));
            } else {
                sender.send(IrcCodec.join(chan));
            }
        }
    }

    // ------------------------------------------------------------------
    // Posting helper used by numerics and the command layer
    // ------------------------------------------------------------------

    /**
     * Drop a message into a named window (or the server status window when
     * name is null).
     */
    public void post(ChannelState explicit, String name, int kind, String text) {
        ChannelState c = explicit;
        if (c == null) {
            c = server.window(chanNameOf(null), ChannelState.TYPE_STATUS);
        }
        if (c == null) {
            return;
        }
        Message m;
        if (kind == Message.SERVER) {
            m = Message.server(System.currentTimeMillis(), text);
        } else {
            m = Message.event(System.currentTimeMillis(), kind, null, text);
        }
        m.target = c.getName();
        c.getRing().add(m);
        c.markUnread(false);
        if (listener != null) {
            listener.onMessage(server, c, m);
        }
    }

    private String chanNameOf(ChannelState c) {
        if (c != null) {
            return c.getName();
        }
        return server.getHost();
    }

    public static boolean isChannelName(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        char c = s.charAt(0);
        return c == '#' || c == '&' || c == '+' || c == '!' || c == ':';
    }
}
