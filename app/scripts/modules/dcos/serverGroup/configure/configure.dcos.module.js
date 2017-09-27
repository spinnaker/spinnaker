'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.configure', [
  require('./configuration.service.js').name,
  require('./CommandBuilder.js').name,
  require('./wizard/basicSettings.controller.js').name,
  require('./wizard/Clone.controller.js').name,
  require('./wizard/containerSettings.controller.js').name,
  require('./wizard/environmentVariables.controller.js').name,
  require('./wizard/healthChecks.controller.js').name,
  require('./wizard/network.controller.js').name,
  require('./wizard/volumes.controller.js').name,
  require('./wizard/optional.controller.js').name
]);
