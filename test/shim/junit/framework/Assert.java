package junit.framework;

/** JUnit 3.8 compatible assertions. See TestCase for why the shim exists. */
public class Assert {

    protected Assert() {
    }

    public static void fail() {
        fail(null);
    }

    public static void fail(String message) {
        AssertionError e = new AssertionError(message == null ? "failed" : message);
        // Attach the caller's location so the console runner can print "file:line".
        StackTraceElement[] t = new Throwable().getStackTrace();
        for (int i = 0; i < t.length; i++) {
            if (t[i].getClassName().startsWith("com.chatterbox.")) {
                e.setStackTrace(new StackTraceElement[] { t[i] });
                break;
            }
        }
        throw e;
    }

    public static void assertTrue(boolean condition) {
        assertTrue(null, condition);
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message == null ? "expected true" : message);
        }
    }

    public static void assertFalse(boolean condition) {
        assertFalse(null, condition);
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message == null ? "expected false" : message, !condition);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(prefix(message) + "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            fail(prefix(message) + "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(String message, long expected, long actual) {
        if (expected != actual) {
            fail(prefix(message) + "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(String message, boolean expected, boolean actual) {
        if (expected != actual) {
            fail(prefix(message) + "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(int expected, int actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(long expected, long actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertNull(String message, Object o) {
        if (o != null) {
            fail((message == null ? "" : message + " ") + "expected null but was <" + o + ">");
        }
    }

    public static void assertNotNull(String message, Object o) {
        if (o == null) {
            fail(message == null ? "expected non-null" : message);
        }
    }

    public static void assertSame(String message, Object a, Object b) {
        if (a != b) {
            fail(message == null ? "expected same instance" : message);
        }
    }

    public static void assertNull(Object o) {
        assertNull(null, o);
    }

    public static void assertNotNull(Object o) {
        assertNotNull(null, o);
    }

    public static void assertSame(Object a, Object b) {
        assertSame(null, a, b);
    }

    public static void assertTrue(boolean condition, String message) {
        assertTrue(message, condition);
    }

    public static void assertFalse(boolean condition, String message) {
        assertFalse(message, condition);
    }

    private static String prefix(String message) {
        return message == null ? "" : message + " ";
    }
}
