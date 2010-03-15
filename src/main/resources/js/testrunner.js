/*
 * Copyright 2009 Bodil Stokke <bodil@bodil.tv>
 * 
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 */

var tried = 0, passed = 0, failed = 0;
var details = {};

if (typeof this.setUp == "function") {
    this.setUp();
}

for (key in this) {
    var member = this[key];
    if (key.indexOf("test") === 0 && typeof member == "function") {
        tried++;
        var timer = getLolTimer();
        try {
            member.call(this);
            passed++;
            details[key] = { success: true, time: getLolTimer() - timer };
        } catch (e) {
            // If somebody threw a string, rethrow it as a proper exception.            
            if (typeof e == "string") {
                try {
                    throw new Error(e);
                } catch (wrapped) {
                    e = wrapped;
                    e.wasString = true;
                }
            }
            
            failed++;
            if (e.rhinoException) {
                e.stackTrace = e.rhinoException.getScriptStackTrace();
            }
            details[key] = { failure: true, exception: e, time: getLolTimer() - timer };
        }
    }
}

if (typeof this.tearDown == "function") {
    this.tearDown();
}

