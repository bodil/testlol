load basePath + "coffeescriptLocal.coffee"

window.testCoffeescriptLoader = ->
    assertTrue true

window.testCoffeescriptGlobal = ->
    assertTrue window.coffeescriptGlobal

window.testCoffeescriptLocal = ->
    assertTrue window.coffeescriptLocal

