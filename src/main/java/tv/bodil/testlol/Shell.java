/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 *
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

package tv.bodil.testlol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

public class Shell extends ScriptableObject {

    private static final long serialVersionUID = 7044452514934467919L;
    private final Testlol testlol;

    @Override
    public String getClassName() {
        return "testlol";
    }

    public Shell(Testlol testlol, Context cx) {
        super();
        this.testlol = testlol;
        cx.initStandardObjects(this);
        String[] names = { "print", "load", "getClasspathResource", "getLolTimer", "readFile", "readUrl" };
        defineFunctionProperties(names, Shell.class, ScriptableObject.DONTENUM);
        Scriptable props = cx.newObject(this);
        props.put("testSuite", props, Context.javaToJS(testlol.getTestSuite(),
                this));
        props.put("basePath", props, Context.javaToJS(testlol.getBasePath(),
                this));
        this.defineProperty("_testlol", props, ScriptableObject.DONTENUM);
    }

    public static Shell getShell(Scriptable thisObj) {
        Shell shell = null;
        ScriptableObject scope = (ScriptableObject) getTopLevelScope(thisObj);
        try {
            shell = (Shell) scope;
        } catch (ClassCastException e) {
            shell = (Shell) getTopLevelScope(scope.getPrototype());
        }
        return shell;
    }

    public static void print(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                out.append(" ");

            // Convert the arbitrary JavaScript value into a string form.
            out.append(Context.toString(args[i]));
        }
        Shell shell = getShell(thisObj);
        shell.testlol.getLog().info(out.toString());
    }

    public static void load(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) {
        Shell shell = getShell(thisObj);
        for (Object arg : args) {
            shell.processSource(cx, Context.toString(arg), thisObj);
        }
    }

    public static String getClasspathResource(Context cx, Scriptable thisObj,
            Object[] args, Function funObj) {
        Shell shell = (Shell) getShell(thisObj);
        try {
            File file = shell.testlol.copyClasspathResource(Context
                    .toString(args[0]));
            return file.getPath();
        } catch (IOException e) {
            throw new JavaScriptException(e.getMessage(), "<testlol>", 1);
        }
    }

    /**
     * Evaluate JavaScript source.
     *
     * @param cx
     *            the current context
     * @param filename
     *            the name of the file to compile, or null for interactive mode.
     */
    private void processSource(Context cx, String filename, Scriptable scope) {
        Reader in = null;
        if (filename.startsWith("classpath:")) {
            String path = filename.substring(10);
            InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(path);
            if (resourceAsStream == null) {
                Context.reportError("Couldn't open classpath resource \"" + path + "\".");
                return;
            }
            in = new InputStreamReader(resourceAsStream);
        } else {
            try {
                in = new InputStreamReader(new URL(filename).openStream());
            } catch (MalformedURLException e) {
                try {
                    in = new FileReader(filename);
                } catch (FileNotFoundException e1) {
                    Context.reportError("Couldn't open file \"" + filename + "\".");
                    return;
                }
            } catch (IOException e) {
                Context.reportError("Couldn't open URL \"" + filename + "\".");
                return;
            }
        }

        try {
            // Here we evalute the entire contents of the file as
            // a script. Text is printed only if the print() function
            // is called.
            cx.evaluateReader(getTopLevelScope(scope), in, filename, 1, null);
        } catch (WrappedException we) {
            System.err.println(we.getWrappedException().toString());
            we.printStackTrace();
        } catch (EvaluatorException ee) {
            System.err.println("js: " + ee.getMessage());
        } catch (JavaScriptException jse) {
            System.err.println("js: " + jse.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
        } finally {
            try {
                in.close();
            } catch (IOException ioe) {
                System.err.println(ioe.toString());
            }
        }
    }

    public static long getLolTimer(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) {
        return Calendar.getInstance().getTimeInMillis();
    }

    // Nicked a few methods from the Rhino shell source to clone readFile/readUrl functions
    
    public static Object readFile(Context cx, Scriptable thisObj,
            Object[] args, Function funObj) throws IOException {
        if (args.length == 0) {
            throw Context.reportRuntimeError("readFile() takes at least one argument.");
        }
        String path = ScriptRuntime.toString(args[0]);
        String charCoding = null;
        if (args.length >= 2) {
            charCoding = ScriptRuntime.toString(args[1]);
        }

        return readUrl(path, charCoding, true);
    }

    public static Object readUrl(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws IOException {
        if (args.length == 0) {
            throw Context.reportRuntimeError("readUrl() takes at least one argument.");
        }
        String url = ScriptRuntime.toString(args[0]);
        String charCoding = null;
        if (args.length >= 2) {
            charCoding = ScriptRuntime.toString(args[1]);
        }

        return readUrl(url, charCoding, false);
    }

    private static String readUrl(String filePath, String charCoding,
            boolean urlIsFile) throws IOException {
        int chunkLength;
        InputStream is = null;
        try {
            if (!urlIsFile) {
                URL urlObj = new URL(filePath);
                URLConnection uc = urlObj.openConnection();
                is = uc.getInputStream();
                chunkLength = uc.getContentLength();
                if (chunkLength <= 0)
                    chunkLength = 1024;
                if (charCoding == null) {
                    String type = uc.getContentType();
                    if (type != null) {
                        charCoding = getCharCodingFromType(type);
                    }
                }
            } else {
                File f = new File(filePath);
                if (!f.exists()) {
                    throw new FileNotFoundException("File not found: "
                            + filePath);
                } else if (!f.canRead()) {
                    throw new IOException("Cannot read file: " + filePath);
                }
                long length = f.length();
                chunkLength = (int) length;
                if (chunkLength != length)
                    throw new IOException("Too big file size: " + length);

                if (chunkLength == 0) {
                    return "";
                }

                is = new FileInputStream(f);
            }

            Reader r;
            if (charCoding == null) {
                r = new InputStreamReader(is);
            } else {
                r = new InputStreamReader(is, charCoding);
            }
            return readReader(r, chunkLength);

        } finally {
            if (is != null)
                is.close();
        }
    }

    private static String readReader(Reader reader, int initialBufferSize)
            throws IOException {
        char[] buffer = new char[initialBufferSize];
        int offset = 0;
        for (;;) {
            int n = reader.read(buffer, offset, buffer.length - offset);
            if (n < 0) {
                break;
            }
            offset += n;
            if (offset == buffer.length) {
                char[] tmp = new char[buffer.length * 2];
                System.arraycopy(buffer, 0, tmp, 0, offset);
                buffer = tmp;
            }
        }
        return new String(buffer, 0, offset);
    }

    private static String getCharCodingFromType(String type)
    {
        int i = type.indexOf(';');
        if (i >= 0) {
            int end = type.length();
            ++i;
            while (i != end && type.charAt(i) <= ' ') {
                ++i;
            }
            String charset = "charset";
            if (charset.regionMatches(true, 0, type, i, charset.length()))
            {
                i += charset.length();
                while (i != end && type.charAt(i) <= ' ') {
                    ++i;
                }
                if (i != end && type.charAt(i) == '=') {
                    ++i;
                    while (i != end && type.charAt(i) <= ' ') {
                        ++i;
                    }
                    if (i != end) {
                        // i is at the start of non-empty
                        // charCoding spec
                        while (type.charAt(end -1) <= ' ') {
                            --end;
                        }
                        return type.substring(i, end);
                    }
                }
            }
        }
        return null;
    }
};
