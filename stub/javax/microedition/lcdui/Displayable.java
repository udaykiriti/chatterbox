package javax.microedition.lcdui;

/** JSR 118 javax.microedition.lcdui.Displayable (compile stub).
 *  repaint()/repaint(x,y,w,h) live on Canvas in real MIDP — they were
 *  mistakenly placed here in an earlier stub, which would fail on devices. */
public abstract class Displayable {
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isShown() { return false; }
    public void addCommand(Command cmd) {}
    public void removeCommand(Command cmd) {}
    public void setCommandListener(CommandListener l) {}
    public String getTitle() { return ""; }
    public void setTitle(String s) {}
    protected void sizeChanged(int w, int h) {}
}
