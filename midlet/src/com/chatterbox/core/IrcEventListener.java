package com.chatterbox.core;

/**
 * Callbacks raised by {@link Session}. Every method is invoked on the socket
 * reader thread; implementations MUST NOT touch the UI directly — they push
 * onto a queue that the MIDlet thread drains in serviceRepaints().
 */
public interface IrcEventListener {

    /** A line to display in the named window. */
    void onMessage(ServerState server, ChannelState window, Message message);

    /** Connection state changed. */
    void onStateChanged(ServerState server, int newState, String statusText);

    /** 433: our nick was taken, Session has already switched to the alt nick. */
    void onNickInUse(ServerState server, String attempted, String chosen);

    /** New private conversation window was created. */
    void onWindowOpened(ServerState server, ChannelState window);

    /** Window was closed by the user or by a KICK/PART. */
    void onWindowClosed(ServerState server, ChannelState window);

    /** Channel topic changed. */
    void onTopicChanged(ServerState server, ChannelState channel, String topic);

    /** The server said ERROR or we lost the socket. Reason is user-presentable. */
    void onError(ServerState server, String reason);

    /** Something worth a beep/vibrate arrived (mention or PM). */
    void onAlert(ServerState server, ChannelState window, boolean isMention, boolean isPrivate);

    /** Round-trip time updated, milliseconds. -1 means unknown. */
    void onLag(ServerState server, long millis);
}
