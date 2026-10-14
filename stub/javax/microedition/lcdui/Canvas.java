package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;
    public static final int KEY_NUM_STAR = 42;
    public static final int KEY_NUM_POUND = 35;
    public static final int KEY_NUM_ENTER = 10;
    public static final int KEY_ENTER = 10;
    public static final int KEY_LEFT = -2;
    public static final int KEY_RIGHT = -5;
    public static final int KEY_UP = -1;
    public static final int KEY_DOWN = -6;
    public static final int KEY_DEL = 8;
    public static final int KEY_BACKSPACE = 8;
    public static final int KEY_CLEAR = 12;
    public static final int KEY_SOFT1 = -4;
    public static final int KEY_SOFT2 = -3;

    protected Canvas() {}
    public final void serviceRepaints() {}
    public void setFullScreenMode(boolean mode) {}
    public boolean hasPointerEvents() { return false; }
    public boolean hasPointerMotionEvents() { return false; }
    public boolean hasRepeatEvents() { return true; }
    public boolean hasDoubleBuffer() { return true; }
    public int getGameAction(int keyCode) { return 0; }
    public String getGameActionName(int keyCode) { return ""; }
    protected void showNotify() {}
    protected void hideNotify() {}
    protected void keyPressed(int keyCode) {}
    protected void keyReleased(int keyCode) {}
    protected void keyRepeated(int keyCode) {}
    protected void pointerPressed(int x, int y) {}
    protected void pointerReleased(int x, int y) {}
    protected void pointerDragged(int x, int y) {}
    protected void paint(Graphics g) {}
}
