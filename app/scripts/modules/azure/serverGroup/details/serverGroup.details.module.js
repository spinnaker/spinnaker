'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.azure', [
  require('./serverGroupDetails.azure.controller').name,
]);
