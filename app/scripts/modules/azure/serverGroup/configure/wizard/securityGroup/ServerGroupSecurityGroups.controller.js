'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.securityGroups.controller', [])
  .controller('azureServerGroupSecurityGroupsCtrl', function($scope) {
    ModalWizard.markClean('security-groups');
    ModalWizard.markComplete('security-groups');

    $scope.command.selectedSecurityGroup = {
      id: $scope.command.securityGroupName,
    };

    this.securityGroupChanged = function(securityGroup) {
      $scope.command.securityGroupName = securityGroup.id;
      ModalWizard.markComplete('security-groups');
    };
  });
