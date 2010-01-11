package tv.bodil.testlol;

import java.net.URL;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;

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

    @Override
    public String getClassName() {
        return "testlol";
    }

    public Shell(Testlol testlol, Context cx) {
        super();
        this.testlol = testlol;
        cx.initStandardObjects(this);
        String[] names = { "print", "load", "getClasspathResource", "getLolTimer" };
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
        Shell shell = (Shell) getTopLevelScope(thisObj);
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
        if(filename.startsWith("http:") || filename.startsWith("https:")){
            try{
                in = new InputStreamReader(new URL(filename).openStream());
            }catch(Exception x){
                Context.reportError("Couldn't open url \"" + filename + "\".");
                return;
            }
        }else{
            try {
                in = new FileReader(filename);
            } catch (FileNotFoundException ex) {
                Context.reportError("Couldn't open file \"" + filename + "\".");
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

};
