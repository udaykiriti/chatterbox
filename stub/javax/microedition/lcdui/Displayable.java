package javax.microedition.lcdui;

public abstract class Displayable {
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isShown() { return false; }
    public void addCommand(Command cmd) {}
    public void removeCommand(Command cmd) {}
    public void setCommandListener(CommandListener l) {}
    public String getTitle() { return ""; }
    public void setTitle(String s) {}
    public void repaint() {}
    public void repaint(int x, int y, int w, int h) {}
    public int getGameAction(int keyCode) { return 0; }
    protected void sizeChanged(int w, int h) {}
}
