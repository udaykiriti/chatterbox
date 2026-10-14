package com.chatterbox.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Vibrator;

/**
 * Haptic and audible notification for mentions and private messages.
 *
 * MIDP 2.0 Vibrator notes that matter on the target devices:
 *  - javax.microedition.lcdui.Vibrator has existed since MIDP 1.0, but on
 *    Nokia S60 3rd edition the handset only vibrates when the profile is NOT
 *    silent and the device is not in "vibrating only" mode with the app
 *    backgrounded. There is no API to detect that, so the user may get nothing.
 *  - play(int sequence) must be called with a pattern whose total duration is a
 *    legal value; a malformed pattern throws IllegalArgumentException on some
 *    firmwares and is silently ignored on others. We keep patterns simple.
 *  - The vibrate() 3-arg form (pattern, repeat, duration) is MIDP 2.0. The
 *    1-arg vibrate(int) is deprecated but works everywhere. We use the 1-arg
 *    form for compatibility and define patterns as alternating on/off durations
 *    in the sequence form only where we need a double buzz.
 *  - vibrate() may throw IllegalStateException if the device does not support
 *    it. Always guard.
 *
 * Beep: MIDP has no tone API. The portable option is an Alert type, but that is
 * intrusive. What we actually do is rely on the system's own new-message
 * indicator, which many S60 builds wire to the app's own alert sound when the
 * app requests an Alert with a timeout. Where a user has set a system sound,
 * that plays. We make the "beep" preference instead control a short
 * Alert(ALERT_TIMEOUT) which S60 pairs with the notification sound.
 */
public final class Notifier {

    /** One short buzz. */
    private static final int PATTERN_MENTION = 1;
    /** Two buzzes, for a PM. */
    private static final int PATTERN_PRIVATE = 3;

    private Vibrator vibrator;
    private boolean vibratorAvailable;
    private boolean beepEnabled = true;
    private boolean vibrateEnabled = true;

    public Notifier() {
        try {
            vibrator = Vibrator.getVibrator();
            vibratorAvailable = vibrator != null;
        } catch (Throwable t) {
            vibratorAvailable = false;
        }
    }

    public void setVibrateEnabled(boolean b) {
        vibrateEnabled = b;
    }

    public void setBeepEnabled(boolean b) {
        beepEnabled = b;
    }

    public boolean isVibrateEnabled() {
        return vibrateEnabled && vibratorAvailable;
    }

    /** @return true when the device actually has a vibrator. */
    public boolean hasVibrator() {
        return vibratorAvailable;
    }

    /**
     * Mention alert. When the app is in the background we also drop a
     * transient screen Alert, which is the only portable way to get the system
     * notification sound on S60.
     */
    public void mention(boolean background) {
        buzz(PATTERN_MENTION, "Mention");
        if (background) {
            notifyOnce("New mention");
        }
    }

    public void privateMessage(boolean background) {
        buzz(PATTERN_PRIVATE, "Private message");
        if (background) {
            notifyOnce("New private message");
        }
    }

    private void buzz(int pattern, String why) {
        if (!vibrateEnabled || !vibratorAvailable) {
            return;
        }
        try {
            int[] seq = patternFor(pattern);
            vibrator.vibrate(seq, -1);
        } catch (Throwable t) {
            // IllegalArgumentException / IllegalStateException on devices
            // without a vibrator or with vibration disabled in the profile.
            vibratorAvailable = false;
        }
    }

    /**
     * Sequence entries alternate vibrate, pause, vibrate, pause...
     * Durations are in milliseconds. Totals here are under 500 ms so they do
     * not overlap with the user's next action.
     */
    private static int[] patternFor(int pattern) {
        if (pattern == PATTERN_PRIVATE) {
            return new int[] { 120, 80, 120 };
        }
        return new int[] { 100 };
    }

    /**
     * Fire-and-forget screen Alert for background notifications. We do not set
     * it as the current displayable when the app is in the foreground, because
     * that would steal the caret from the input line.
     */
    private void notifyOnce(String text) {
        if (!beepEnabled) {
            return;
        }
        try {
            Display d = Handler.currentDisplay();
            if (d == null) {
                return;
            }
            Alert a = new Alert(text);
            a.setTimeout(2000);
            javax.microedition.lcdui.Displayable cur = d.getCurrent();
            if (cur == null) {
                return;
            }
            // setCurrent(alert, next) restores the previous screen on timeout.
            d.setCurrent(a, cur);
        } catch (Throwable t) {
            // Some firmwares throw IllegalStateException when an Alert is
            // already on screen. Swallow it; a missed beep is not a crash.
        }
    }

    /**
     * Tiny indirection so Alert does not have to know about the MIDlet.
     * ChatterboxMIDlet installs the display at boot.
     */
    public static final class Handler {
        private static Display display;

        private Handler() {
        }

        public static void install(Display d) {
            display = d;
        }

        static Display currentDisplay() {
            return display;
        }
    }
}
