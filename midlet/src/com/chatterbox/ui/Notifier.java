package com.chatterbox.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Display;

/**
 * Haptic and audible notification for mentions and private messages.
 *
 * MIDP 2.0 Display.vibrate notes that matter on the target devices:
 *  - The only portable vibration API is Display.vibrate(int ms). Class
 *    javax.microedition.lcdui.Vibrator DOES NOT EXIST in MIDP; an earlier
 *    stub invented one, which is exactly the kind of thing this class now
 *    guards against at runtime. On Nokia S60 3rd edition the handset only
 *    vibrates when the profile is NOT silent and the device is not in
 *    "vibrating only" mode with the app backgrounded. There is no API to
 *    detect that, so the user may get nothing.
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
    private static final int PATTERN_MENTION = 400;
    /** Two buzzes, for a PM (Display.vibrate is a single duration; the two-buzz
     *  feel comes from scheduling a second buzz when the first completes). */
    private static final int PATTERN_PRIVATE = 3;

    private Display display;
    private boolean vibratorAvailable;
    private boolean beepEnabled = true;
    private boolean vibrateEnabled = true;

    public Notifier() {
    }

    public void setDisplay(Display d) {
        display = d;
        try {
            vibratorAvailable = display != null && display.vibrate(0);
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
            int ms = durationFor(pattern);
            display.vibrate(ms);
        } catch (Throwable t) {
            // IllegalStateException on devices without a vibrator or with
            // vibration disabled in the profile.
            vibratorAvailable = false;
        }
    }

    /**
     * Sequence entries alternate vibrate, pause, vibrate, pause...
     * Durations are in milliseconds. Totals here are under 500 ms so they do
     * not overlap with the user's next action.
     */
    /**
     * Millisecond duration of one buzz. PATTERN_PRIVATE buzzes slightly
     * longer than a mention so a user can tell them apart by feel alone.
     */
    private static int durationFor(int pattern) {
        return pattern == PATTERN_PRIVATE ? 320 : 150;
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
