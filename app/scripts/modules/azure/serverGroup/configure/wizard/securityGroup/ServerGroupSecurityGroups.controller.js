'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.azure.serverGroup.configure.securityGroups.controller', [
  V2_MODAL_WIZARD_SERVICE,
  ])
  .controller('azureServerGroupSecurityGroupsCtrl', function($scope, v2modalWizardService) {
    v2modalWizardService.markClean('security-groups');
    v2modalWizardService.markComplete('security-groups');

    this.securityGroupChanged = function(securityGroup) {
      $scope.command.securityGroupName = securityGroup.id;
      v2modalWizardService.markComplete('security-groups');
    };
  });
