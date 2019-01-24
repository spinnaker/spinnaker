import { configure } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

configure({ adapter: new Adapter() });

Error.stackTraceLimit = Infinity;

// jquery has to be first or many a test will break
global.$ = global.jQuery = require('jquery');

require('./settings');
require('./app/scripts/app');

// angular 1 test harnesss
const angular = require('angular');
require('angular-mocks');
beforeEach(angular.mock.module('bcherny/ngimport'));

require('./test/helpers/customMatchers');

const testContext = require.context('./app/scripts/', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);
