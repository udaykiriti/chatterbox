package junit.framework;

/**
 * Minimal JUnit 3 shim.
 *
 * Why this exists: the deliverable must be runnable on a plain desktop JDK with
 * no network access to fetch junit.jar. These three classes are API-compatible
 * with JUnit 3.8 for everything ProtocolParserTest uses, so when you DO have a
 * real junit.jar on the classpath you simply delete test/shim and the same
 * source compiles and runs under the real runner.
 *
 * Not part of the MIDlet JAR.
 */
public class TestCase extends Assert {

    private String name;

    public TestCase() {
        this.name = getClass().getName();
    }

    public TestCase(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    public int countTestCases() {
        return 1;
    }

    public void runBare() throws Throwable {
        setUp();
        try {
            runTest();
        } finally {
            tearDown();
        }
    }

    /** Overridden by the shim runner via method scanning. */
    public void runTest() throws Throwable {
    }

    public String toString() {
        return name;
    }
}
