'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.securityGroups.controller', [
  require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
  ])
  .controller('azureServerGroupSecurityGroupsCtrl', function($scope, v2modalWizardService) {
    v2modalWizardService.markClean('security-groups');
    v2modalWizardService.markComplete('security-groups');

    this.securityGroupChanged = function(securityGroup) {
      $scope.command.securityGroupName = securityGroup.id;
      v2modalWizardService.markComplete('security-groups');
    };
  });
