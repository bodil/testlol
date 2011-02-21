load(basePath + "OpenLayers-2.10.js");

function testOLVersion() {
    assertEquals("OpenLayers 2.10 -- $Revision: 10721 $", OpenLayers.VERSION_NUMBER);
}

function testBasicDOMManipulation() {
    var div = document.createElement("div");
    div.id = "foo";
    document.body.appendChild(div);
    OpenLayers.Element.addClass(OpenLayers.Util.getElement("foo"), "bar");
    assertEquals("bar", div.getAttribute("class"));
    document.body.removeChild(div);
}
