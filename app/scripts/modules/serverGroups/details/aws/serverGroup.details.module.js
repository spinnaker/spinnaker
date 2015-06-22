'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws', [
  require('./serverGroupDetails.aws.controller.js'),
  require('./autoScalingProcess.service.js'),
  require('./modifyScalingProcesses.controller.js'),
]);
