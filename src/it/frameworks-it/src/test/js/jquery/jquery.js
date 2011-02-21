load(basePath + "jquery-1.5.js");

function testJQueryVersion() {
    assertEquals("1.5", jQuery.fn.jquery);
}

function testBasicDOMManipulation() {
    var div = document.createElement("div");
    div.id = "foo";
    document.body.appendChild(div);
    $("#foo").addClass("bar");
    assertEquals("bar", div.getAttribute("class"));
    document.body.removeChild(div);
}
