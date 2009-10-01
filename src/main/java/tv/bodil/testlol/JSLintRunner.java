package tv.bodil.testlol;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.logging.Log;

import com.googlecode.jslint4java.Issue;
import com.googlecode.jslint4java.JSLint;

public class JSLintRunner {
    private List<File> sources = new LinkedList<File>();
    private final File basePath;

    public JSLintRunner(File basePath) {
        this.basePath = basePath;
        findSources(this.basePath);
    }

    private void findSources(File path) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                findSources(file);
            }
        } else if (path.isFile()) {
            if (path.getName().endsWith(".js")) {
                sources.add(path);
            }
        }
    }

    public int lint(Log log) throws IOException {
        int errors = 0;
        JSLint jsLint = new JSLint();
        String pathPrefix = basePath.getCanonicalPath();
        for (File file : sources) {
            String path = file.getCanonicalPath();
            if (path.startsWith(pathPrefix)) {
                path = path.substring(pathPrefix.length() + 1);
            }
            FileReader in = new FileReader(file);
            List<Issue> issues = jsLint.lint(path, in);
            if (issues.size() > 0) {
                errors += issues.size();
                log.error("In file " + path + ":");
                for (Issue issue : issues) {
                    log.error("  " + issue.toString());
                }
            }
        }
        return errors;
    }
}
