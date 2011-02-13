function testAsserts() {
	assert(true);
	assertTrue(true);
	assertFalse(false);
	assertEquals(1337, 1337);
	assertEquals("flarp", "flarp");
	assertNotEquals(1337, "flarp");
	assertNotEquals("foo", "bar");
	assertNotEquals(NaN, NaN);
	assertNull(null);
	assertNotNull("flarp");
	assertNotNull({});
	assertUndefined(undefined);
	assertUndefined(window.some_undefined_variable);
	assertNotUndefined("foo");
	assertNotUndefined(1337);
	assertNaN(NaN);
	assertNaN(0 / 0);
	assertNotNaN(1337);
	assertObjectEquals({ foo: "foo", bar: 1337 }, { foo: "foo", bar: 1337 })
	assertArrayEquals([ 1, 2, "foo" ], [ 1, 2, "foo" ]);
	assertHTMLEquals("<div>foo</div>", "<DIV>foo</DIV>");
	assertEvaluatesToTrue("foo");
	assertEvaluatesToTrue({});
	assertEvaluatesToFalse("");
	assertHashEquals({ foo: "foo", bar: 1337 }, { foo: "foo", bar: 1337 });
	assertRoughlyEquals(5, 6, 2);
	assertContains("foo", [ 1, 2, "foo", "bar" ]);
}

var setUpCalled = false;

function setUp() {
	setUpCalled = true;
}

function testSetUpCalled() {
	assertTrue(setUpCalled);
}
