'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.securityGroups.controller', [])
  .controller('azureServerGroupSecurityGroupsCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');
  });
