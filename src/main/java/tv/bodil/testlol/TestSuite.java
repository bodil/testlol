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
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

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

    public boolean runTests(Scriptable scope, Context cx, Script testRunner, Log log) throws MojoExecutionException {
        boolean testsFailed = false;
        for (File file : tests) {
            // Copy the global scope to attempt to isolate the test's environment
            Scriptable testScope = cx.newObject(scope);
            testScope.setPrototype(scope);
            testScope.setParentScope(null);

            try {
                log.info("Running test " + file.getPath());
                FileReader in = new FileReader(file);
                cx.evaluateReader(scope, in, file.getPath(), 1, null);
                testRunner.exec(cx, testScope);
                Integer tried = (Integer) Context.jsToJava(testScope.get("tried", testScope), Integer.class);
                Integer passed = (Integer) Context.jsToJava(testScope.get("passed", testScope), Integer.class);
                Integer failed = (Integer) Context.jsToJava(testScope.get("failed", testScope), Integer.class);
                log.info(String.format("%d test%s: %d passed, %d failed", tried, (tried == 1) ? "" : "s", passed, failed));
                if (failed > 0) {
                    testsFailed = true;
                }
            } catch (FileNotFoundException e) {
                throw new MojoExecutionException(e.getMessage());
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage());
            }
        }
        return testsFailed;
    }
}

