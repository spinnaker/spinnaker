'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.securityGroup', [])
  .controller('gceServerGroupSecurityGroupsCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');

  });
