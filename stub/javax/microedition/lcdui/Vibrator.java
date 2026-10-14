package javax.microedition.lcdui;

public final class Vibrator {
    private Vibrator() {}
    public static Vibrator getVibrator() { return new Vibrator(); }
    public void vibrate(int duration) {}
    public void vibrate(int[] pattern) {}
    public void vibrate(int[] pattern, int repeat) {}
    public void vibrate(int pattern, int repeat, int duration) {}
}
