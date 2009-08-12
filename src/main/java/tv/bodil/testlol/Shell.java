package tv.bodil.testlol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

public class Shell extends ScriptableObject {

    private static final long serialVersionUID = 7044452514934467919L;
    private final Testlol testlol;

    public String getClassName() {
        return "testlol";
    }

    public Shell(Testlol testlol, Context cx) {
        super();
        this.testlol = testlol;
        cx.initStandardObjects(this);
        String[] names = { "print", "load", "getClasspathResource" };
        defineFunctionProperties(names, Shell.class, ScriptableObject.DONTENUM);
        Scriptable props = cx.newObject(this);
        Object[] files = new Object[testlol.getGlobalFiles().length];
        for (int i = 0; i < testlol.getGlobalFiles().length; i++) {
            files[i] = testlol.getGlobalFiles()[i].toString();
        }
        props.put("globalFiles", props, cx.newArray(this, files));
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

    public static void print(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
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

    public static void load(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Shell shell = getShell(thisObj);
        for (int i = 0; i < args.length; i++) {
            shell.processSource(cx, Context.toString(args[i]));
        }
    }

    public static String getClasspathResource(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Shell shell = (Shell) getTopLevelScope(thisObj);
        try {
            File file = shell.testlol.copyClasspathResource(Context.toString(args[0]));
            return file.getPath();
        } catch (IOException e) {
            throw new JavaScriptException(e.getMessage(), "<testlol>", 1);
        }
    }

    /**
     * Evaluate JavaScript source.
     *
     * @param cx the current context
     * @param filename the name of the file to compile, or null
     *                 for interactive mode.
     */
    private void processSource(Context cx, String filename) {
        if (filename == null) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String sourceName = "<stdin>";
            int lineno = 1;
            boolean hitEOF = false;
            do {
                int startline = lineno;
                System.err.print("js> ");
                System.err.flush();
                try {
                    String source = "";
                    // Collect lines of source to compile.
                    while (true) {
                        String newline;
                        newline = in.readLine();
                        if (newline == null) {
                            hitEOF = true;
                            break;
                        }
                        source = source + newline + "\n";
                        lineno++;
                        // Continue collecting as long as more lines
                        // are needed to complete the current
                        // statement.  stringIsCompilableUnit is also
                        // true if the source statement will result in
                        // any error other than one that might be
                        // resolved by appending more source.
                        if (cx.stringIsCompilableUnit(source))
                            break;
                    }
                    Object result = cx.evaluateString(this, source, sourceName, startline, null);
                    if (result != Context.getUndefinedValue()) {
                        System.err.println(Context.toString(result));
                    }
                } catch (WrappedException we) {
                    // Some form of exception was caught by JavaScript and
                    // propagated up.
                    System.err.println(we.getWrappedException().toString());
                    we.printStackTrace();
                } catch (EvaluatorException ee) {
                    // Some form of JavaScript error.
                    System.err.println("js: " + ee.getMessage());
                } catch (JavaScriptException jse) {
                    // Some form of JavaScript error.
                    System.err.println("js: " + jse.getMessage());
                } catch (IOException e) {
                    System.err.println("js: " + e.getMessage());
                }
            } while (!hitEOF);
            System.err.println();
        } else {
            FileReader in = null;
            try {
                in = new FileReader(filename);
            } catch (FileNotFoundException ex) {
                Context.reportError("Couldn't open file \"" + filename + "\".");
                return;
            }

            try {
                // Here we evalute the entire contents of the file as
                // a script. Text is printed only if the print() function
                // is called.
                cx.evaluateReader(this, in, filename, 1, null);
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
    }

};
