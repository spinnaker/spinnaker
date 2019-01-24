'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.openstack', [
  require('./wizard/upsert.controller').name,
]);
