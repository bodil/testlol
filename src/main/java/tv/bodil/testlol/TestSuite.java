/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 *
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

package tv.bodil.testlol;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class TestSuite {

    private List<File> tests = new LinkedList<File>();
    private final File testSuitePath;
    private final File reportPath;

    public TestSuite(File path, File reportPath) throws MojoExecutionException {
        this.testSuitePath = path;
        this.reportPath = reportPath;
        reportPath.mkdirs();
        for (File file : reportPath.listFiles()) {
            file.delete();
        }
        tests = ScriptLoader.findTests(path);
        Collections.sort(tests);
    }

    public int runTests(Shell shell, Context cx, Script testRunner, Log log)
            throws MojoExecutionException, IOException {
        int total = 0, totalFailed = 0;
        String pathPrefix = testSuitePath.getCanonicalPath();
        for (File file : tests) {
            String path = file.getCanonicalPath();
            if (path.startsWith(pathPrefix)) {
                path = path.substring(pathPrefix.length() + 1).replace(
                        File.separatorChar, '.');
            }

            // Copy the global scope to attempt to isolate the test's
            // environment
            Scriptable testScope = cx.newObject(shell);
            testScope.setPrototype(shell);
            testScope.setParentScope(null);

            try {
                log.info("Running test " + path);
                ScriptLoader.evaluateScript(cx, testScope, file, path);
                testRunner.exec(cx, testScope);
                Integer tried = (Integer) Context.jsToJava(testScope.get(
                        "tried", testScope), Integer.class);
                Integer passed = (Integer) Context.jsToJava(testScope.get(
                        "passed", testScope), Integer.class);
                Integer failed = (Integer) Context.jsToJava(testScope.get(
                        "failed", testScope), Integer.class);
                Scriptable details = (Scriptable) testScope.get("details",
                        testScope);
                log.info(String.format("  %d test%s: %d passed, %d failed",
                        tried, (tried == 1) ? "" : "s", passed, failed));
                reportTestCase(path, details, tried, failed);
                if (failed > 0) {
                    log.info("");
                    for (Object id : details.getIds()) {
                        ScriptableObject detail = (ScriptableObject) details
                                .get(Context.toString(id), details);
                        if (detail.has("failure", detail)) {
                            printError(log, detail, path, Context
                                    .toString(id));
                        }
                    }
                    log.info("");
                    totalFailed += failed;
                }
                total += passed;
            } catch (FileNotFoundException e) {
                throw new MojoExecutionException(e.getMessage());
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage());
            }
        }
        log.info("");
        log.info("Total tests passed: "
                + total
                + ((totalFailed > 0) ? (", total tests FAILED: " + totalFailed)
                        : ""));
        log.info("");
        return totalFailed;
    }

    private void reportTestCase(String path, Scriptable details, int total,
            int failed) throws MojoExecutionException {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .newDocument();
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage());
        }
        Element root = doc.createElement("testsuite");
        doc.appendChild(root);
        root.setAttribute("tests", "" + total);
        root.setAttribute("failures", "" + failed);
        root.setAttribute("name", path);
        root.setAttribute("timestamp", new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        Double totalTime = 0.0;
        for (Object id : details.getIds()) {
            ScriptableObject detail = (ScriptableObject) details.get(Context
                    .toString(id), details);
            Element testCase = doc.createElement("testcase");
            testCase.setAttribute("classname", path);
            testCase.setAttribute("name", Context.toString(id));
            Double testTime = ((Double) Context.jsToJava(detail.get("time", detail), Double.class)) / 1000.0;
            testCase.setAttribute("time", String.format("%.3f", testTime));
            totalTime += testTime;
            if (detail.has("failure", detail)) {
                Scriptable error = (Scriptable) detail.get(
                        "exception", detail);
                Element failure = doc.createElement("failure");
                String msg = "";
                if (error.has("isJsUnitException", error)) {
                    failure.setAttribute("type", "JsUnitException");
                    msg = error.get("jsUnitMessage", error).toString();
                } else if (error.has("rhinoException", error)) {
                    failure.setAttribute("type", error.get("name", error)
                            .toString());
                    msg = error.get("name", error) + ": "
                            + error.get("message", error);
                } else {
                    msg = error.toString();
                }
                failure.setAttribute("message", msg);
                StringBuilder body = new StringBuilder();
                body.append(msg + "\n");
                if (error.has("stackTrace", error)) {
                    NativeJavaObject stackWrap = (NativeJavaObject) error.get("stackTrace", error);
                    String[] stack = ((String) stackWrap.unwrap()).split("\n");
                    for (String trace : stack) {
                        if (!trace.contains("classpath:/")) {
                            body.append(trace + "\n");
                        }
                    }
                }
                failure.setTextContent(body.toString());
                testCase.appendChild(failure);
            }
            root.appendChild(testCase);
        }
        root.setAttribute("time", String.format("%.3f", totalTime));

        File report = new File(reportPath, "TEST-" + path + ".xml");
        Source source = new DOMSource(doc);
        Transformer xformer;
        try {
            report.createNewFile();
            Result result = new StreamResult(new FileOutputStream(report));
            xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private void printError(Log log, ScriptableObject detail, String file,
            String test) {
        try {
            Scriptable error = (Scriptable) detail.get("exception", detail);
            if (error.has("isJsUnitException", error)) {
                log.error(test + "() FAILED: " + error.get("jsUnitMessage", error));
            } else if (error.has("rhinoException", error)) {
                // Exception e = (Exception)
                // Context.jsToJava(error.get("rhinoException", error),
                // Exception.class);
                log.error(test + "() FAILED: " + error.get("name", error) + ": "
                        + error.get("message", error));
            }

            NativeJavaObject stackWrap = (NativeJavaObject) error.get("stackTrace",
                    error);
            String[] stack = ((String) stackWrap.unwrap()).split("\n");
            for (String trace : stack) {
                if (!trace.contains("classpath:/")) {
                    log.error(trace);
                }
            }
        } catch (ClassCastException e) {
            String error = (detail.get("exception", detail)).toString();
            log.error(test + "() FAILED: Unhandled exception thrown in test code: " + error);
        }
    }
}
