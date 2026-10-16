package javax.microedition.lcdui;

/**
 * Compile-time stub of the MIDP 2.0 List API, matching the platform
 * signatures exactly (JSR 118). See stub/README.
 *
 * NOTE on the type constants: the desktop emulator build (MicroEmulator,
 * 2010 build) declares EXCLUSIVE=1 / MULTIPLE=2 / IMPLICIT=3, which is
 * inverted from JSR 118. Choice constants are compile-time constants that
 * get inlined into class files, so this stub must carry the EMULATOR's
 * values. A device build via WTK compiles against the real jars and picks
 * up the spec values there; nothing here is packaged or shipped.
 */
public class List extends Displayable {
    public static final int IMPLICIT = 3;
    public static final int EXCLUSIVE = 1;
    public static final int MULTIPLE = 2;
    /** NON-SPEC alias kept for the project sources; same mode as EXCLUSIVE. */
    public static final int SINGLE = 1;
    /** NON-SPEC alias; never used at runtime. */
    public static final int COMBOBOX = 1;

    public List(String title, int listType) {}
    public int append(String stringElement) { return 0; }
    public int append(String stringElement, Image imageElement) { return 0; }
    public void insert(int elementNum, String stringElement) {}
    public void set(int elementNum, String stringElement, Image imageElement) {}
    public void delete(int elementNum) {}
    public void deleteAll() {}
    public int size() { return 0; }
    public String getString(int elementNum) { return ""; }
    public int getSelectedIndex() { return -1; }
    public int getSelectedFlags(boolean[] selectedArray) { return 0; }
    public void setSelectedFlags(boolean[] selectedArray) {}
    public void setSelectedIndex(int elementNum, boolean selected) {}
    public boolean isSelected(int elementNum) { return false; }
}
