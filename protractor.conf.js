exports.config = {
  seleniumServerJar: './node_modules/selenium-server/lib/runner/selenium-server-standalone-2.44.0.jar',
  chromeDriver: './node_modules/chromedriver/lib/chromedriver/chromedriver',
  //seleniumAddress: 'http://localhost:4444/wd/hub',

  framework: 'jasmine',

  baseUrl: 'http://0.0.0.0:9000',
  specs: 'test/e2e/**/*.js',
  getPageTimeout: 20000,
  allScriptsTimeout: 20000,
  //directConnect:true,
  capabilities: {
    'browserName': 'chrome',
  },
  onPrepare: function() {
    //browser.driver.manage().window().setSize(1500, 800);

    require('jasmine-reporters');
    jasmine.getEnv().addReporter(
      new jasmine.JUnitXmlReporter('xmloutput', true, true)
    );
  }
};
