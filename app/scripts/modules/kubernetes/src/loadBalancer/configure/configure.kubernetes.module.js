'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.configure.kubernetes', [
  require('./wizard/upsert.controller.js').name,
  require('./wizard/ports.controller.js').name,
  require('./wizard/advancedSettings.controller.js').name,
]);
