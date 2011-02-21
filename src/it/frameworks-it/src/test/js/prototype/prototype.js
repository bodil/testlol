load(basePath + "prototype-1.7.js");

function testPrototypeVersion() {
    assertEquals("1.7", Prototype.Version);
}

function testBasicDOMManipulation() {
    var div = document.createElement("div");
    div.id = "foo";
    document.body.appendChild(div);
    $("foo").addClassName("bar");
    assertEquals("bar", div.getAttribute("class"));
    document.body.removeChild(div);
}

function testBasicDOMManipulationUsingElementClass() {
    // Same as above, only specifically use the Element class instead of $
    var div = document.createElement("div");
    div.id = "foo";
    document.body.appendChild(div);
    Element.addClassName("foo", "bar");
    assertEquals("bar", div.getAttribute("class"));
    document.body.removeChild(div);
}
