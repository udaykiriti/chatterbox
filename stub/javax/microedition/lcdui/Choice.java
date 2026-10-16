package javax.microedition.lcdui;

/**
 * JSR 118 javax.microedition.lcdui.Choice.
 *
 * Stubs exist only so the MIDlet compiles on a desktop JDK (CI + MicroEmulator
 * runs). Signatures and constant VALUES mirror the reference implementation;
 * builds for real devices compile against the WTK's midpapi20.jar instead.
 */
public interface Choice {
    int EXCLUSIVE = 1;
    int MULTIPLE = 2;
    int IMPLICIT = 3;
    int POPUP = 4;

    int append(String stringElement, Image imageElement);
    void delete(int elementNum);
    void deleteAll();
    String getString(int elementNum);
    int getSelectedFlags(boolean[] selectedArray_return);
    int getSelectedIndex();
    boolean isSelected(int elementNum);
    void setSelectedFlags(boolean[] selectedArray);
    void setSelectedIndex(int elementNum, boolean selected);
    int size();
}
