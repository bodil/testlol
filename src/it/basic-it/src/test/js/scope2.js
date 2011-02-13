// Assert that persistentGlobalScope is persistent by asserting that changes made in scope1.js still exist
function testPersistentGlobalScope() {
	assertTrue(persistentGlobalScope.flarp);
}

// Assert that volatileLocalScope is volatile by asserting changes made in scope1.js didn't persist
function testVolatileLocalScope() {
	var isUndefined = false;
	try {
		assertUndefined(volatileLocalScope);
	} catch (e) {
		isUndefined = true;
	}
	assertTrue(isUndefined);
}
