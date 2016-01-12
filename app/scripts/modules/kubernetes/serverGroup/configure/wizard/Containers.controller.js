'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.containers', [])
  .controller('kubernetesServerGroupContainersController', function(modalWizardService) {
    modalWizardService.getWizard().markClean('containers');
    modalWizardService.getWizard().markComplete('containers');

    this.cpuPattern = /^\d+(m)?$/;
    this.memoryPattern = /^\d+(Mi|Gi)?$/;

    return;
  });
