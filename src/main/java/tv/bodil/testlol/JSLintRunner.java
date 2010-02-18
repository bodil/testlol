/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 *
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

package tv.bodil.testlol;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.googlecode.jslint4java.Issue;
import com.googlecode.jslint4java.JSLint;
import com.googlecode.jslint4java.Option;

public class JSLintRunner {
    private List<File> sources = new LinkedList<File>();
    private final File basePath;
    private JSLint jsLint;

    public JSLintRunner(File basePath, String options) throws IOException, MojoExecutionException {
        jsLint = new JSLint();

        if (options != null) {
            String[] optionsList = options.split(" +");
            for (String option : optionsList) {
                String value = "true";
                String[] flarp = option.split("=");
                if (flarp.length > 2) {
                    throw new MojoExecutionException("Invalid JSLint option \"" + option + "\"");
                } else if (flarp.length == 2) {
                    option = flarp[0];
                    value = flarp[1];
                }
                Option o = getOption(option);
                if (o == null) {
                    throw new MojoExecutionException("Invalid JSLint option \"" + option + "\"");
                }
                jsLint.addOption(o, value);
            }
        }

        this.basePath = basePath;
        findSources(this.basePath);
    }

    private Option getOption(String optName) {
        try {
            return Option.valueOf(optName.toUpperCase(Locale.getDefault()));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
