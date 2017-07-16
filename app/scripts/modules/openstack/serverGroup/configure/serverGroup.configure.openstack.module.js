'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure', [
  require('./ServerGroupCommandBuilder.js'),
  require('./serverGroupConfiguration.service.js'),
  require('./wizard/location/ServerGroupBasicSettings.controller.js'),
  require('./wizard/instance/ServerGroupInstanceSettings.controller.js'),
  require('./wizard/access/AccessSettings.controller.js'),
  require('./wizard/advanced/advancedSettings.component.js'),
  require('./wizard/Clone.controller.js'),
]);
