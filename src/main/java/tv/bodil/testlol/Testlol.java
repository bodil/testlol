package tv.bodil.testlol;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
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
 * Goal which touches a timestamp file.
 *
 * @goal test
 *
 * @phase process-sources
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
    private Script[] globalScripts;

    private long timer;

    private void startTimer() {
        timer = Calendar.getInstance().getTimeInMillis();
    }

    private void markTimer(String what) {
        long time = Calendar.getInstance().getTimeInMillis() - timer;
        getLog().debug("Time spent " + what + ": " + time + " ms");
    }

    private Script loadJSResource(Context cx, String path) throws IOException {
        Reader in = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path));
        return cx.compileReader(in, "classpath:" + path, 1, null);
    }

    private void execJSResource(Context cx, Scriptable scope, String path) throws IOException {
        loadJSResource(cx, path).exec(cx, scope);
    }

    public File copyClasspathResource(String path) throws IOException {
        File source = new File(path);
        File tempfile = File.createTempFile(source.getName(), ".tmp");
        tempfile.deleteOnExit();
        BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(
                path)));
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
            // Load global files

            TestSuite tests = new TestSuite(getTestSuite());

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
                    execJSResource(cx, shell, file.getPath());
                }
                markTimer("loading global scripts");
            }

            Script testRunner = loadJSResource(cx, "/js/testrunner.js");

            startTimer();
            int failed = tests.runTests(shell, cx, testRunner, getLog());
            markTimer("running test suite");
            if (failed > 0) {
                throw new MojoFailureException(failed + " test" + (failed == 1 ? "" : "s") + " failed");
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
