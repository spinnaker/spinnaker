'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.openstack', [
  require('./wizard/rules.controller.js'),
  require('./wizard/upsert.controller.js'),
]);
