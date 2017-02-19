Error.stackTraceLimit = Infinity;

// jquery has to be first or many a test will break
global.$ = global.jQuery = require('jquery');

// angular 1 test harnesss
require('angular');
require('angular-mocks');

// polyfills
require('core-js/es6');
require('core-js/es7/reflect');

// not sure, but angular 2 says these are required
// there's definitely some overlap with the polyfills
require('core-js/client/shim');
require('reflect-metadata');

// for consolidating TS-generated helpers
// should be able to remove for TS 2.2
require('ts-helpers');

require('zone.js/dist/zone');
require('zone.js/dist/long-stack-trace-zone');
require('zone.js/dist/proxy');
require('zone.js/dist/sync-test');
require('zone.js/dist/jasmine-patch');
require('zone.js/dist/async-test');
require('zone.js/dist/fake-async-test');

require('rxjs/Rx');

require('./settings.js');

const testContext = require.context('./app/scripts/', true, /\.spec\.(js|ts)$/);
testContext.keys().forEach(testContext);

const testing = require('@angular/core/testing');
const browser = require('@angular/platform-browser-dynamic/testing');

testing.TestBed.initTestEnvironment(browser.BrowserDynamicTestingModule, browser.platformBrowserDynamicTesting());
