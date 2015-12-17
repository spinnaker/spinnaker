'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws', [
  require('./serverGroupDetails.aws.controller.js'),
  require('./scalingProcesses/autoScalingProcess.service.js'),
  require('./scalingProcesses/modifyScalingProcesses.controller.js'),
  require('./scheduledAction/editScheduledActions.modal.controller.js'),
  require('./advancedSettings/editAsgAdvancedSettings.modal.controller.js'),
]);
