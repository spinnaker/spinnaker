'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.kubernetes', [
  require('./wizard/backend.controller.js').name,
  require('./wizard/rules.controller.js').name,
  require('./wizard/upsert.controller.js').name,
]);
