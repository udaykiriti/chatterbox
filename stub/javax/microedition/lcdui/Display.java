package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

/** JSR 118 javax.microedition.lcdui.Display (compile stub). */
public class Display {
    public static final int COLOR_BACKGROUND = 0;
    public static final int COLOR_FOREGROUND = 1;
    public static final int COLOR_HIGHLIGHTED_BACKGROUND = 2;
    public static final int COLOR_HIGHLIGHTED_FOREGROUND = 3;
    public static final int COLOR_BORDER = 4;
    public static final int COLOR_HIGHLIGHTED_BORDER = 5;

    protected Display() {}

    public static Display getDisplay(MIDlet m) { return new Display(); }
    public Displayable getCurrent() { return null; }
    public void setCurrent(Displayable d) {}
    public void setCurrent(Alert a, Displayable next) {}
    public void callSerially(Runnable r) {}
    public boolean flashBacklight(int ms) { return false; }
    public boolean vibrate(int ms) { return false; }
    public int numColors() { return 0; }
    public boolean isColor() { return true; }
}
