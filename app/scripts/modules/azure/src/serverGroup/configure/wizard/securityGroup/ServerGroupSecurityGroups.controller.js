'use strict';

import { module } from 'angular';

import { ModalWizard } from '@spinnaker/core';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.securityGroups.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPS_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPS_CONTROLLER, []).controller(
  'azureServerGroupSecurityGroupsCtrl',
  [
    '$scope',
    function ($scope) {
      ModalWizard.markClean('security-groups');
      ModalWizard.markComplete('security-groups');

      $scope.command.selectedSecurityGroup = {
        id: $scope.command.securityGroupName,
      };

      this.securityGroupChanged = function (securityGroup) {
        $scope.command.securityGroupName = securityGroup.id;
        ModalWizard.markComplete('security-groups');
      };
    },
  ],
);
