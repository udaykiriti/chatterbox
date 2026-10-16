package javax.microedition.lcdui;

/**
 * Compile-time stub of the MIDP 2.0 ChoiceGroup API, matching the platform
 * signatures exactly (JSR 118). See stub/README.
 *
 * Constant values here match the desktop emulator (see List.java note);
 * device builds compile against the real MIDP jars instead.
 */
public class ChoiceGroup extends Item {
    public static final int IMPLICIT = 3;
    public static final int EXCLUSIVE = 1;
    public static final int MULTIPLE = 2;
    public static final int POPUP = 4;

    public ChoiceGroup(String label, int choiceType) { super(label); }
    public ChoiceGroup(String label, int choiceType, String[] stringElements, Image[] imageElements) {
        super(label);
    }
    public int append(String stringElement) { return 0; }
    public int append(String stringElement, Image imageElement) { return 0; }
    public void insert(int elementNum, String stringElement, Image imageElement) {}
    public void set(int elementNum, String stringElement, Image imageElement) {}
    public void delete(int elementNum) {}
    public void deleteAll() {}
    public int size() { return 0; }
    public String getString(int elementNum) { return ""; }
    public int getSelectedIndex() { return 0; }
    public int getSelectedFlags(boolean[] selectedArray) { return 0; }
    public void setSelectedFlags(boolean[] selectedArray) {}
    public void setSelectedIndex(int elementNum, boolean selected) {}
    public boolean isSelected(int elementNum) { return false; }
}
