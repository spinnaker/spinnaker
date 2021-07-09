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
import { jasmineMockHttpSupport } from './packages/core/src/api/mock/jasmine';

// angular 1 test harness
import 'angular';
import 'angular-mocks';
beforeEach(angular.mock.module('bcherny/ngimport'));

jasmineMockHttpSupport();

let testContext;

testContext = require.context('./packages/amazon', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/appengine', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/azure', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/cloudfoundry', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/core', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/dcos', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/docker', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/ecs', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/google', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/huaweicloud', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/kubernetes', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/oracle', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/tencentcloud', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);

testContext = require.context('./packages/titus', true, /\.spec\.(js|ts|tsx)$/);
testContext.keys().forEach(testContext);
