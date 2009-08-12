var tried = 0, passed = 0, failed = 0;

var failedDetails = {};

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
        } catch (e) {
            failed++;
            failedDetails[key] = e;
        }
    }
}

if (typeof this.tearDown == "function") {
    this.tearDown();
}
