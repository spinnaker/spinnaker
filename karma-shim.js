/* eslint-disable @spinnaker/import-sort */
import { configure } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

configure({ adapter: new Adapter() });

Error.stackTraceLimit = Infinity;

// jquery has to be first or many a test will break
global.$ = global.jQuery = require('jquery');

import './app/scripts/modules/app/src/settings';
import './app/scripts/modules/app/src/app';
import './test/helpers/customMatchers';
import { jasmineMockHttpSupport } from './app/scripts/modules/core/src/api/mock/jasmine';

// angular 1 test harness
import 'angular';
import 'angular-mocks';
beforeEach(angular.mock.module('bcherny/ngimport'));

jasmineMockHttpSupport();

const testContext = require.context('./app/scripts/', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);
