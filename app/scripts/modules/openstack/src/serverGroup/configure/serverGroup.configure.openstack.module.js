'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure', [
  require('./ServerGroupCommandBuilder.js').name,
  require('./serverGroupConfiguration.service.js').name,
  require('./wizard/location/ServerGroupBasicSettings.controller.js').name,
  require('./wizard/instance/ServerGroupInstanceSettings.controller.js').name,
  require('./wizard/access/AccessSettings.controller.js').name,
  require('./wizard/advanced/advancedSettings.component.js').name,
  require('./wizard/Clone.controller.js').name,
]);
