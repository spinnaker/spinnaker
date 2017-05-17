'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes', [
  require('./configuration.service.js'),
  require('./CommandBuilder.js'),
  require('./wizard/BasicSettings.controller.js'),
  require('./wizard/Clone.controller.js'),
  require('./wizard/loadBalancers.controller.js'),
  require('./wizard/templateSelection.controller.js'),
  require('./wizard/volumes.controller.js'),
  require('./wizard/deployment.controller.js'),
]);
