'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.openstack', [
  require('../../../core/account/account.module.js'),
  require('./wizard/upsert.controller.js'),
]);
