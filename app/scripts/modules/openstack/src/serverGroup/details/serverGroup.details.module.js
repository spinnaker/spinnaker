'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.details', [
  require('./serverGroupDetails.openstack.controller').name,
  require('./resize/resizeServerGroup.controller').name,
  require('./rollback/rollbackServerGroup.controller').name,
]);
