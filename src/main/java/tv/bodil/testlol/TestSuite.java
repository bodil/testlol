package tv.bodil.testlol;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class TestSuite {

    private List<File> tests = new LinkedList<File>();

    public TestSuite(File path) {
        findTests(path);
    }

    private void findTests(File path) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                findTests(file);
            }
        } else if (path.isFile()) {
            if (path.getName().endsWith(".js")) {
                tests.add(path);
            }
        }
    }

    public int runTests(Shell shell, Context cx, Script testRunner, Log log) throws MojoExecutionException {
        int total = 0;
        for (File file : tests) {
            // Copy the global scope to attempt to isolate the test's environment
            Scriptable testScope = cx.newObject(shell);
            testScope.setPrototype(shell);
            testScope.setParentScope(null);

            try {
                log.info("Running test " + file.getPath());
                FileReader in = new FileReader(file);
                cx.evaluateReader(testScope, in, file.getPath(), 1, null);
                testRunner.exec(cx, testScope);
                Integer tried = (Integer) Context.jsToJava(testScope.get("tried", testScope), Integer.class);
                Integer passed = (Integer) Context.jsToJava(testScope.get("passed", testScope), Integer.class);
                Integer failed = (Integer) Context.jsToJava(testScope.get("failed", testScope), Integer.class);
                Scriptable failedDetails = (Scriptable) testScope.get("failedDetails", testScope);
                log.info(String.format("  %d test%s: %d passed, %d failed", tried, (tried == 1) ? "" : "s", passed,
                        failed));
                if (failed > 0) {
                    log.info("");
                    for (Object id : failedDetails.getIds()) {
                        ScriptableObject detail = (ScriptableObject) failedDetails.get(Context.toString(id),
                                failedDetails);
                        printError(log, detail, file.getPath(), Context.toString(id));
                    }
                    log.info("");
                    return failed;
                }
                total += passed;
            } catch (FileNotFoundException e) {
                throw new MojoExecutionException(e.getMessage());
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage());
            }
        }
        log.info("");
        log.info("Total tests passed: " + total);
        log.info("");
        return 0;
    }

    private void printError(Log log, ScriptableObject error, String file, String test) {
        if (error.has("isJsUnitException", error)) {
            log.error(test + "() FAILED: " + error.get("jsUnitMessage", error));
        }
        else if (error.has("rhinoException", error)) {
            // Exception e = (Exception) Context.jsToJava(error.get("rhinoException", error), Exception.class);
            log.error(test + "() FAILED: " + error.get("name", error) + ": " + error.get("message", error));
        }
        NativeJavaObject stackWrap = (NativeJavaObject) error.get("stackTrace", error);
        String[] stack = ((String) stackWrap.unwrap()).split("\n");
        for (String trace : stack) {
            if (!trace.contains("classpath:/")) {
                log.error(trace);
            }
        }
    }
}
