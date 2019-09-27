'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.kubernetes', [
  require('./wizard/backend.controller').name,
  require('./wizard/rules.controller').name,
  require('./wizard/tls.controller').name,
  require('./wizard/upsert.controller').name,
]);
