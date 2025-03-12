'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCEARCHETYPE_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.instanceArchetype.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCEARCHETYPE_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCEARCHETYPE_CONTROLLER, []).controller(
  'azureInstanceArchetypeCtrl',
  [
    '$scope',
    'instanceTypeService',
    'modalWizardService',
    function ($scope, instanceTypeService, modalWizardService) {
      const wizard = modalWizardService.getWizard();

      $scope.$watch('command.viewState.instanceProfile', function () {
        if (!$scope.command.viewState.instanceProfile || $scope.command.viewState.instanceProfile === 'custom') {
          wizard.excludePage('instance-type');
        } else {
          wizard.includePage('instance-type');
          wizard.markClean('instance-profile');
          wizard.markComplete('instance-profile');
        }
      });

      $scope.$watch('command.viewState.instanceType', function (newVal) {
        if (newVal) {
          wizard.markClean('instance-profile');
          wizard.markComplete('instance-profile');
        }
      });
    },
  ],
);
