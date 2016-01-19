'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.capacity', [])
  .controller('kubernetesServerGroupCapacityController', function(modalWizardService) {
    modalWizardService.getWizard().markClean('capacity');
    modalWizardService.getWizard().markComplete('capacity');
  });
