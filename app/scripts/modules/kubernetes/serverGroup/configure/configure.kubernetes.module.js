'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes', [
  require('../../../core/account/account.module.js'),
  require('./configuration.service.js'),
  require('./wizard/BasicSettings.controller.js'),
  require('./wizard/LoadBalancers.controller.js'),
  require('./wizard/SecurityGroups.controller.js'),
  require('./wizard/Clone.controller.js'),
  require('./wizard/Containers.controller.js'),
  require('./wizard/Capacity.controller.js'),
]);
