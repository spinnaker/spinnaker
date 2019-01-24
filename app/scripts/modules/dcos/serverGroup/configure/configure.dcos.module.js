'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.configure', [
  require('./configuration.service').name,
  require('./CommandBuilder').name,
  require('./wizard/basicSettings.controller').name,
  require('./wizard/Clone.controller').name,
  require('./wizard/containerSettings.controller').name,
  require('./wizard/environmentVariables.controller').name,
  require('./wizard/healthChecks.controller').name,
  require('./wizard/network.controller').name,
  require('./wizard/volumes.controller').name,
  require('./wizard/optional.controller').name,
]);
