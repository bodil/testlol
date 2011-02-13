// Test that loading files from the classpath works.
load("classpath:JsUtil.js");

function testClasspathJsUtil() {
	// JsUtil.js contains a simple class JsUnitError we'll test to ensure the file loaded and parsed.
	var error = new JsUnitError("oops");
	assertEquals("JsUnitError: oops", error.toString());
}
