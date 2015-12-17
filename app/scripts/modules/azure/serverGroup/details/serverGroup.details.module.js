'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.azure', [
  require('./serverGroupDetails.azure.controller.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  require('./scalingProcesses/modifyScalingProcesses.controller.js'),
  require('./scheduledAction/editScheduledActions.modal.controller.js'),
  require('./advancedSettings/editAsgAdvancedSettings.modal.controller.js'),
]);
