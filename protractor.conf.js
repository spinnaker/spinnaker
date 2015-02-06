var phantomjs = require('phantomjs');

exports.config = {
  //seleniumServerJar: 'node_modules/selenium-server/lib/runner/selenium-server-standalone-2.43.1.jar',
  seleniumAddress: 'http://localhost:4444/wd/hub',
  baseUrl: 'http://0.0.0.0:9000',
  specs: 'test/e2e/**/*.js',
  getPageTimeout: 20000,
  directConnect:true,
  capabilities: {
    'browserName': 'chrome',
  },
  onPrepare: function() {
    browser.driver.manage().window().setSize(1500, 800);
  }
};
