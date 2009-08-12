var tried = 0, passed = 0, failed = 0;
var details = {};

if (typeof this.setUp == "function") {
    this.setUp();
}

for (key in this) {
    var member = this[key];
    if (key.indexOf("test") === 0 && typeof member == "function") {
        tried++;
        try {
            member.call(this);
            passed++;
            details[key] = { success: true };
        } catch (e) {
            failed++;
            if (e.rhinoException) {
                e.stackTrace = e.rhinoException.getScriptStackTrace();
            }
            details[key] = { failure: true, exception: e };
        }
    }
}

if (typeof this.tearDown == "function") {
    this.tearDown();
}

