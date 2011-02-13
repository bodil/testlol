function testPersistentGlobalScope() {
	assertFalse(persistentGlobalScope.flarp);
	persistentGlobalScope.flarp = true;
}

function setUp() {
	volatileLocalScope = true;
}

function testVolatileLocalScope() {
	assertTrue(volatileLocalScope);
}
