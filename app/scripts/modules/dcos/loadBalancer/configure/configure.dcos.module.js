'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.loadBalancer.configure', [
  require('./wizard/upsert.controller.js').name,
  require('./wizard/resources.controller.js').name,
  require('./wizard/ports.controller.js').name,
]);
