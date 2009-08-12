(function($env){
    
    $env(getClasspathResource("/html/index.html"), {
        //let it load the script from the html
        scriptTypes: {
            "text/javascript"   :true
        }
        /*
        afterload:{
            'qunit/testrunner.js': function(){
                //hook into qunit.log
                var count = 0;
                QUnit.log = function(result, message){
                    $env.log('(' + (count++) + ')[' + 
                        ((!!result) ? 'PASS' : 'FAIL') + '] ' + message);
                };
                //hook into qunit.done
                QUnit.done = function(pass, fail){
                    $env.warn('Writing Results to File');
                    jQuery('script').each(function(){
                        this.type = 'text/envjs';
                    });
                    $env.writeToFile(
                        document.documentElement.xml, 
                        $env.location('jqenv.html')
                    );
                };
            }
        }
        */
    });
    
})(Envjs);

var basePath = _testlol.basePath + "/";
