'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.loadBalancer.configure', [
  require('./wizard/upsert.controller').name,
  require('./wizard/resources.controller').name,
  require('./wizard/ports.controller').name,
]);
