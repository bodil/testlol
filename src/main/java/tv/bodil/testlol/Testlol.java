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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Calendar;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

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

    public void execute() throws MojoExecutionException {
        Context cx = new ContextFactory().enterContext();

        try {
            // Load global files
            startTimer();
            globalScripts = new Script[globalFiles.length];
            for (int i = 0; i < globalFiles.length; i++) {
                File file = globalFiles[i];
                getLog().info("Loading global script " + file.toString());
                Reader in = new FileReader(file);
                Script script = cx.compileReader(in, file.toString(), 1, null);
                globalScripts[i] = script;
            }
            markTimer("compiling global scripts");

            getLog().info("Running test suite in " + testSuite.toString());

            startTimer();
            Scriptable scope = cx.initStandardObjects();
            markTimer("initStandardObjects()");
            Object logger = Context.javaToJS(getLog(), scope);
            ScriptableObject.putProperty(scope, "_testlol_logger", logger);
            cx.evaluateString(scope,
                            "function print() { var s = ''; for (var i=0;i<arguments.length;i++) s+=arguments[i]; _testlol_logger.info(s); }",
                            "<internal>", 1, null);
            for (int i = 0; i < globalScripts.length; i++) {
                Script script = globalScripts[i];
                script.exec(cx, scope);
            }
            markTimer("initialising scope for test");
            startTimer();
            Object result = cx.evaluateString(scope, "window;", "<cmd>", 1,
                    null);
            getLog().info(Context.toString(result));
            markTimer("running test");
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } finally {
            Context.exit();
        }
    }
}
