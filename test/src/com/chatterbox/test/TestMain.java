package com.chatterbox.test;

import junit.framework.TestRunner;

/**
 * Standalone entry point so the suite runs with nothing but a JDK:
 *
 *   java -cp build/classes:build/stub com.chatterbox.test.TestMain
 *
 * With a real junit.jar on the classpath (and test/shim removed) the same test
 * classes run under "junit.textui.TestRunner". The Ant "test" target does
 * whichever applies.
 */
public final class TestMain {

    private static final Class[] SUITES = {
        ProtocolParserTest.class,
        CoreTest.class,
        SessionTest.class,
        CryptoTest.class,
        X25519Test.class,
        SecurityTest.class,
        E2ETest.class,
        RatchetTest.class
    };

    public static void main(String[] args) {
        int totalPassed = 0;
        int totalFailed = 0;
        StringBuffer failures = new StringBuffer();

        System.out.println();
        System.out.println("  Chatterbox test suite");
        System.out.println("  " + new StringBuffer("---------------------"));

        for (int i = 0; i < SUITES.length; i++) {
            System.out.println();
            System.out.println("  \u001B[36m" + SUITES[i].getName() + "\u001B[0m");
            try {
                TestRunner runner = new TestRunner();
                runner.run(SUITES[i]);
                totalPassed += runner.getPassed();
                totalFailed += runner.getFailed();
                failures.append(runner.failures());
            } catch (Throwable t) {
                totalFailed++;
                failures.append("  " + SUITES[i].getName() + ": " + t + "\n");
            }
        }

        System.out.println();
        System.out.println("  " + new StringBuffer("---------------------"));
        if (totalFailed == 0) {
            System.out.println("  \u001B[32m\u2714 " + totalPassed + " tests, all green\u001B[0m");
        } else {
            System.out.println("  \u001B[31m\u2718 " + totalFailed + " failed, "
                    + totalPassed + " passed\u001B[0m");
            System.out.println(failures);
        }
        System.out.println();
        System.exit(totalFailed == 0 ? 0 : 1);
    }

    private TestMain() {
    }
}
