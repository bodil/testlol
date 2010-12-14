/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 *
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

package tv.bodil.testlol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
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
    
    /** @parameter default-value="${project}" */
    private MavenProject project;
    
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
    private String[] globalFiles;

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
        // We can't use getClass().getClassLoader().getResourceAsStream() on maven 3
        // it returns null if the path starts with /. This was a maven 2 bug.

        getLog().debug("loadJSResource.load : " + path);


        // In order to support both maven 2 and maven 3, we try to turn around the maven
        // 2 bug. The strategy is to try to load with the given path and if not found try
        // to load the same resource by without the first / (if the first character is a /)
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            getLog().debug("Resource not found " + path + ", try a remove the first /");
            if (path.startsWith("/")) {
                path = path.substring(1); // Remove the first /
                is = getClass().getClassLoader().getResourceAsStream(path);
            }
        }

        // To be defensive, we just throw an exception here.
        if (is == null) {
            throw new IOException("cannot load resource : " + path);
        }

        Reader in = new InputStreamReader(is);
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

        // In order to support both maven 2 and maven 3, we try to turn around the maven
        // 2 bug. The strategy is to try to load with the given path and if not found try
        // to load the same resource by without the first / (if the first character is a /)
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            getLog().debug("Resource not found " + path + ", try a remove the first /");
            if (path.startsWith("/")) {
                path = path.substring(1); // Remove the first /
                is = getClass().getClassLoader().getResourceAsStream(path);
            }
        }

        // To be defensive, we just throw an exception here.
        if (is == null) {
            throw new IOException("cannot load resource : " + path);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(is));
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

            cx.setOptimizationLevel(-1);

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
                for (String path : globalFiles) {
                    if (path.startsWith("classpath:")) {
                        path = path.substring(10);
                        getLog().info("Loading classpath:" + path);
                        execJSResource(cx, shell, path);
                    } else {
                        File file = new File(path);
                        if (!file.isAbsolute()) {
                            file = new File(project.getBasedir(), path);
                        }
                        getLog().info("Loading " + file.getPath());
                        cx.evaluateReader(shell, new FileReader(file), file
                                .getPath(), 1, null);
                    }
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
