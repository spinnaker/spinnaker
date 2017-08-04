'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.configure', [
  require('./configuration.service.js'),
  require('./CommandBuilder.js'),
  require('./wizard/basicSettings.controller.js'),
  require('./wizard/Clone.controller.js'),
  require('./wizard/containerSettings.controller.js'),
  require('./wizard/environmentVariables.controller.js'),
  require('./wizard/healthChecks.controller.js'),
  require('./wizard/network.controller.js'),
  require('./wizard/volumes.controller.js'),
  require('./wizard/optional.controller.js')
]);
