'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure', [
  require('./ServerGroupCommandBuilder').name,
  require('./serverGroupConfiguration.service').name,
  require('./wizard/location/ServerGroupBasicSettings.controller').name,
  require('./wizard/instance/ServerGroupInstanceSettings.controller').name,
  require('./wizard/access/AccessSettings.controller').name,
  require('./wizard/advanced/advancedSettings.controller').name,
  require('./wizard/Clone.controller').name,
]);
