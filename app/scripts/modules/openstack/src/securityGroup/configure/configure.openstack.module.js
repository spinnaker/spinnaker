'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.openstack', [
  require('./wizard/rules.controller').name,
  require('./wizard/upsert.controller').name,
]);
