'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.kubernetes', [
  require('./wizard/backend.controller.js'),
  require('./wizard/rules.controller.js'),
  require('./wizard/upsert.controller.js'),
]);
