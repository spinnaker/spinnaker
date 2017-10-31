'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes', [
  require('./configuration.service.js').name,
  require('./CommandBuilder.js').name,
  require('./wizard/BasicSettings.controller.js').name,
  require('./wizard/advancedSettings.controller.js').name,
  require('./wizard/Clone.controller.js').name,
  require('./wizard/loadBalancers.controller.js').name,
  require('./wizard/volumes.controller.js').name,
  require('./wizard/deployment.controller.js').name,
]);
