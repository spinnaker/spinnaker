'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.details', [
  require('./serverGroupDetails.openstack.controller.js').name,
  require('./resize/resizeServerGroup.controller.js').name,
  require('./rollback/rollbackServerGroup.controller.js').name
]);
