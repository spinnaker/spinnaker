Error.stackTraceLimit = Infinity;

// jquery has to be first or many a test will break
global.$ = global.jQuery = require('jquery');

// angular 1 test harnesss
require('angular');
require('angular-mocks');

// polyfills
require('core-js/client/shim');

require('rxjs/Rx');

require('./settings.js');

const testContext = require.context('./app/scripts/', true, /\.spec\.(js|ts)$/);
testContext.keys().forEach(testContext);
