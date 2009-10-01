package tv.bodil.testlol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * @goal test
 * @phase test
 */
public class Testlol extends AbstractMojo {
    /**
     * Location of the test suite.
     *
     * @parameter
     * @required
     */
    private File testSuite;

    /**
     * Location of the files under testing.
     *
     * @parameter
     * @required
     */
    private File basePath;

    /**
     * List of Javascript files to include in every scope.
     *
     * @parameter
     */
    private File[] globalFiles;

    /**
     * Path for report generation.
     *
     * @parameter expression="${project.build.directory}/surefire-reports"
     */
    private File reportPath;

    /**
     * Run JSLint.
     *
     * @parameter expression="true"
     */
    private boolean jsLint;

    /**
     * JSLint issues are considered errors.
     *
     * @parameter expression="false"
     */
    private boolean jsLintStrict;

    /**
     * Path to run JSLint on, if different from basePath.
     *
     * @parameter
     */
    private File jsLintBasePath;

    /**
     * Options for JSLint, in the format "option1=value1 option2=value2 ..."
     *
     * @parameter
     */
    private String jsLintOptions;

    private long timer;

    private void startTimer() {
        timer = Calendar.getInstance().getTimeInMillis();
    }

    private void markTimer(String what) {
        long time = Calendar.getInstance().getTimeInMillis() - timer;
        getLog().debug("Time spent " + what + ": " + time + " ms");
    }

    private Script loadJSResource(Context cx, String path) throws IOException {
        Reader in = new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream(path));
        return cx.compileReader(in, "classpath:" + path, 1, null);
    }

    private void execJSResource(Context cx, Scriptable scope, String path)
            throws IOException {
        loadJSResource(cx, path).exec(cx, scope);
    }

    public File copyClasspathResource(String path) throws IOException {
        File source = new File(path);
        File tempfile = File.createTempFile(source.getName(), ".tmp");
        tempfile.deleteOnExit();
        BufferedReader in = new BufferedReader(new InputStreamReader(getClass()
                .getClassLoader().getResourceAsStream(path)));
        BufferedWriter out = new BufferedWriter(new FileWriter(tempfile));
        char[] buf = new char[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        return tempfile;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        Context cx = new ContextFactory().enterContext();

        try {
            // Run JSLint

            if (jsLint) {
                if (jsLintBasePath == null) {
                    jsLintBasePath = basePath;
                }
                getLog().info("Running JSLint in " + jsLintBasePath);
                JSLintRunner jsLintRunner = new JSLintRunner(jsLintBasePath, jsLintOptions);
                int failed = jsLintRunner.lint(getLog());
                if (jsLintStrict && failed > 0) {
                    throw new MojoFailureException("JSLint found " + failed + " issue" + (failed == 1 ? "." : "s."));
                }
            }

            // Load global files

            TestSuite tests = new TestSuite(testSuite, reportPath);

            getLog().info("Running test suite in " + getTestSuite().toString());

            startTimer();
            Shell shell = new Shell(this, cx);
            markTimer("initStandardObjects()");
            getLog().info("Loading Env.js");
            execJSResource(cx, shell, "/js/env.rhino.js");
            execJSResource(cx, shell, "/js/testinit.js");
            execJSResource(cx, shell, "/js/jsUnitCore.js");
            markTimer("loading environment");
            if (globalFiles != null) {
                startTimer();
                for (File file : globalFiles) {
                    getLog().info("Loading " + file.getPath());
                    cx.evaluateReader(shell, new FileReader(file), file
                            .getPath(), 1, null);
                }
                markTimer("loading global scripts");
            }

            Script testRunner = loadJSResource(cx, "/js/testrunner.js");

            startTimer();
            int failed = tests.runTests(shell, cx, testRunner, getLog());
            markTimer("running test suite");
            if (failed > 0) {
                throw new MojoFailureException(failed + " test"
                        + (failed == 1 ? "" : "s") + " failed");
            }
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (EcmaError e) {
            throw new MojoFailureException(e.getMessage());
        } finally {
            Context.exit();
        }
    }

    public File getTestSuite() {
        return this.testSuite;
    }

    public File getBasePath() {
        return this.basePath;
    }
}
