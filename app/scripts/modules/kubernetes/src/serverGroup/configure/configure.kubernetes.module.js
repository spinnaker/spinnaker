'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes', [
  require('./configuration.service').name,
  require('./CommandBuilder').name,
  require('./wizard/BasicSettings.controller').name,
  require('./wizard/advancedSettings.controller').name,
  require('./wizard/Clone.controller').name,
  require('./wizard/loadBalancers.controller').name,
  require('./wizard/volumes.controller').name,
  require('./wizard/deployment.controller').name,
]);
