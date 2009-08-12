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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
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
        getLog().info("Time spent " + what + ": " + time + " ms");
    }

    private Script loadJSResource(Context cx, String path) throws IOException {
        Reader in = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path));
        return cx.compileReader(in, "classpath:" + path, 1, null);
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

    public void execute() throws MojoExecutionException {
        Context cx = new ContextFactory().enterContext();

        try {
            // Load global files
            startTimer();
            getLog().info("Compiling Env.js");
            Script envjs = loadJSResource(cx, "/js/env.rhino.js");
            markTimer("compiling Env.js");
            Script testinit = loadJSResource(cx, "/js/testinit.js");
            Script jsUnit = loadJSResource(cx, "/js/jsUnitCore.js");

            TestSuite tests = new TestSuite(testSuite);

            startTimer();
            getLog().info("Compiling global scripts");
            globalScripts = new Script[globalFiles.length];
            for (int i = 0; i < globalFiles.length; i++) {
                getLog().info("Compiling " + globalFiles[i].getPath());
                globalScripts[i] = cx.compileReader(new FileReader(globalFiles[i]), globalFiles[i].getPath(), 1, null);
            }
            markTimer("compiling global scripts");

            getLog().info("Running test suite in " + testSuite.toString());

            startTimer();
            Shell shell = new Shell(this, cx);
            markTimer("initStandardObjects()");
            envjs.exec(cx, shell);
            markTimer("initialising Env.js");
            jsUnit.exec(cx, shell);
            testinit.exec(cx, shell);
            startTimer();
            for (Script script : globalScripts) {
                script.exec(cx, shell);
            }
            markTimer("initialising global scripts");

            Script testRunner = loadJSResource(cx, "/js/testrunner.js");

            startTimer();
            tests.runTests(shell, cx, testRunner, getLog());
            markTimer("running test suite");
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } finally {
            Context.exit();
        }
    }

    public File[] getGlobalFiles() {
        return this.globalFiles;
    }
}
