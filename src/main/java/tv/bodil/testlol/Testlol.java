/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 *
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

package tv.bodil.testlol;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Calendar;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
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
 * @configurator include-project-dependencies
 * @requiresDependencyResolution test
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
    	getLog().debug("Loading JavaScript resource: " + path);
    	return ScriptLoader.compileScript(cx, path);
    }
    
    private void execJSResource(Context cx, Scriptable scope, String path)
            throws IOException {
        loadJSResource(cx, path).exec(cx, scope);
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        final ContextFactory contextFactory = new ContextFactory();

        try {
            ClassLoader cl = getClassLoader();
            contextFactory.initApplicationClassLoader(cl);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (DependencyResolutionRequiredException e) {
            throw new RuntimeException(e);
        }

        Context cx = contextFactory.enterContext();

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
            execJSResource(cx, shell, "/tv/bodil/testlol/js/env.rhino.js");
            execJSResource(cx, shell, "/tv/bodil/testlol/js/testinit.js");
            execJSResource(cx, shell, "/tv/bodil/testlol/js/jsUnitCore.js");
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

            Script testRunner = loadJSResource(cx, "/tv/bodil/testlol/js/testrunner.js");

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

    public ClassLoader getClassLoader() throws MalformedURLException, DependencyResolutionRequiredException {
        @SuppressWarnings("unchecked")
        List<String> classpathFiles = project.getTestClasspathElements();

        URL[] urls = new URL[classpathFiles.size()];

        for (int i = 0; i < classpathFiles.size(); ++i) {
            final String artifact = classpathFiles.get(i);
            getLog().debug("Testlol classpath artifact: " + artifact);
            urls[i] = new File(artifact).toURI().toURL();
        }

        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }
}
