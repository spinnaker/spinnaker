'use strict';

const angular = require('angular');

import { CAPACITY_SELECTOR } from './wizard/capacity/capacitySelector.component';

module.exports = angular.module('spinnaker.serverGroup.configure.titus', [
  require('./wizard/deployInitializer.controller.js').name,
  require('./serverGroupConfiguration.service.js').name,
  require('./wizard/ServerGroupBasicSettings.controller.js').name,
  require('./wizard/ServerGroupResources.controller.js').name,
  require('./wizard/ServerGroupParameters.controller.js').name,
  require('./serverGroupBasicSettingsSelector.directive.js').name,
  CAPACITY_SELECTOR,
]);
