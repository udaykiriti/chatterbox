package junit.framework;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * JUnit 3.8 compatible test runner. TestMain in the chatterbox test package
 * delegates here, so dropping in the real junit.jar changes nothing.
 */
public class TestRunner {

    private int passed;
    private int failed;
    private final StringBuilder failures = new StringBuilder();

    public int run(Class c) throws Exception {
        Object[] instances = newInstance(c);
        for (int i = 0; i < instances.length; i++) {
            Object instance = instances[i];
            Method[] methods = c.getMethods();
            sortByName(methods);
            for (int m = 0; m < methods.length; m++) {
                Method method = methods[m];
                if (!isTest(method)) {
                    continue;
                }
                runOne(instance, method);
            }
        }
        return failed;
    }

    private void runOne(Object instance, Method method) {
        String label = instance.getClass().getSimpleName() + "." + method.getName();
        try {
            ((TestCase) instance).setUp();
            try {
                method.invoke(instance, new Object[0]);
            } finally {
                ((TestCase) instance).tearDown();
            }
            passed++;
            System.out.println("  \u001B[32mPASS\u001B[0m  " + label);
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            failed++;
            failures.append("  ").append(label).append(": ").append(cause).append('\n');
            System.out.println("  \u001B[31mFAIL\u001B[0m  " + label);
            System.out.println("        " + cause + atLine(cause));
        }
    }

    /** First stack frame inside the test class, so failures point at a line. */
    private static String atLine(Throwable t) {
        StackTraceElement[] trace = t.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            if (trace[i].getClassName().startsWith("com.chatterbox.")) {
                return "  @ " + trace[i].getFileName() + ":" + trace[i].getLineNumber();
            }
        }
        return "";
    }

    private static boolean isTest(Method m) {
        return m.getName().startsWith("test")
                && m.getParameterTypes().length == 0
                && Modifier.isPublic(m.getModifiers())
                && !Modifier.isStatic(m.getModifiers());
    }

    private static Object[] newInstance(Class c) throws Exception {
        Object all = c.newInstance();
        if (all instanceof TestCase) {
            return new Object[] { all };
        }
        // Not a TestCase: treat it as a plain suite holder, run methods on it.
        return new Object[] { all };
    }

    private static void sortByName(Method[] methods) {
        for (int i = 1; i < methods.length; i++) {
            Method cur = methods[i];
            String cn = cur.getName();
            int j = i - 1;
            while (j >= 0 && methods[j].getName().compareTo(cn) > 0) {
                methods[j + 1] = methods[j];
                j--;
            }
            methods[j + 1] = cur;
        }
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public String failures() {
        return failures.toString();
    }
}
