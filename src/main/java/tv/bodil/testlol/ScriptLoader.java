package tv.bodil.testlol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class ScriptLoader {
	
    private static final Pattern regex = Pattern.compile("/.*?/\\.\\.");

	public static boolean isScript(File path) {
		return path.isFile()
				&& (path.getName().endsWith(".js")
				 || path.getName().endsWith(".coffee"));
	}

	public static List<File> findTests(File path) {
		List<File> tests = new LinkedList<File>();
		if (path.isDirectory()) {
			for (File file : path.listFiles()) {
				tests.addAll(findTests(file));
			}
		} else if (path.isFile()) {
			if (isScript(path)) {
				tests.add(path);
			}
		}
		return tests;
	}
	
	private static String readReader(Reader reader) throws IOException {
		BufferedReader in = new BufferedReader(reader);
		StringBuffer out = new StringBuffer();
		char[] buffer = new char[4096];
		int l;
		while ((l = in.read(buffer)) != -1)
			out.append(String.valueOf(buffer, 0, l));
		return out.toString();
	}
	
	private static String readScript(Reader reader, String name) throws IOException {
		String script = readReader(reader);
		if (name.endsWith(".coffee")) script = compileCoffeescript(script);
		return script;
	}
	
	private static String compileCoffeescript(String script) throws IOException {
		final ContextFactory contextFactory = new ContextFactory();
		Context cx = contextFactory.enterContext();
		ScriptableObject scope = cx.initStandardObjects();
		InputStreamReader in = new InputStreamReader(loadClasspathResource(cx, "tv/bodil/testlol/js/coffee-script.js"));
		cx.evaluateReader(scope, in, null, 1, null);
		scope.put("compileme", scope, script);
		return cx.evaluateString(scope, "CoffeeScript.compile(compileme)", null, 1, null).toString();
	}

	public static void evaluateScript(Context cx, Scriptable scope, File file, String path) throws IOException {
		cx.evaluateString(scope, readScript(new FileReader(file), path), path, 1, null);
	}

	public static Script compileScript(Context cx, String path) throws IOException {
        Reader in = new InputStreamReader(loadClasspathResource(cx, path));
        return cx.compileString(readScript(in, path), "classpath:" + path, 1, null);
	}

    private static InputStream loadClasspathResource(Context cx, String path) throws IOException {
        Matcher matcher = regex.matcher(path);
        while (matcher.find()) {
            path = path.replace(matcher.group(), "");
        }
        InputStream inputStream = cx.getApplicationClassLoader().getResourceAsStream(path);
        // In order to support both maven 2 and maven 3, we try to turn around the maven
        // 2 bug. The strategy is to try to load with the given path and if not found try
        // to load the same resource by without the first / (if the first character is a /)
        if (inputStream == null) {
            if (path.startsWith("/")) {
                path = path.substring(1); // Remove the first /
                inputStream = cx.getApplicationClassLoader().getResourceAsStream(path);
            }
        }
        if (inputStream == null) {
            throw new IOException("Unable to load resource: " + path);
        }
        return inputStream;
    }

    public static File copyClasspathResource(Context cx, String path) throws IOException {
        File source = new File(path);
        File tempfile = File.createTempFile(source.getName(), ".tmp");
        tempfile.deleteOnExit();

        // In order to support both maven 2 and maven 3, we try to turn around the maven
        // 2 bug. The strategy is to try to load with the given path and if not found try
        // to load the same resource by without the first / (if the first character is a /)

        BufferedReader in = new BufferedReader(new InputStreamReader(loadClasspathResource(cx, path)));
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

}
