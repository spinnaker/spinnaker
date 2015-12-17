'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws', [
  require('../../../core/account/account.module.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('./wizard/ServerGroupBasicSettings.controller.js'),
  require('./wizard/ServerGroupLoadBalancers.controller.js'),
  require('./wizard/capacity/ServerGroupCapacity.controller.js'),
  require('./wizard/ServerGroupInstanceArchetype.controller.js'),
  require('./wizard/ServerGroupInstanceType.controller.js'),
  require('./wizard/securityGroups/ServerGroupSecurityGroups.controller.js'),
  require('./wizard/ServerGroupAdvancedSettings.controller.js'),
  require('../serverGroup.transformer.js'),
  require('../../../core/serverGroup/configure/common/instanceArchetypeSelector.js'),
  require('../../../core/serverGroup/configure/common/instanceTypeSelector.js')
]);
