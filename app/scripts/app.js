'use strict';

// deck is reliant on a million jquery features; we need to load it before angular so that angular does not
// try to use its jqLite implementation.

window.$ = window.jQuery = require('jquery');

let angular = require('angular');

import {APPENGINE_MODULE} from './modules/appengine/appengine.module';

module.exports = angular.module('netflix.spinnaker', [
  require('./modules/netflix/netflix.module'),
  require('./modules/core/core.module'),
  require('./modules/amazon/aws.module'),
  require('./modules/google/gce.module'),
  require('./modules/cloudfoundry/cf.module'),
  require('./modules/titus/titus.module'),
  require('./modules/azure/azure.module'),
  require('./modules/kubernetes/kubernetes.module'),
  require('./modules/openstack/openstack.module'),
  require('./modules/docker/docker.module'),
  APPENGINE_MODULE,
]);
