'use strict';

const angular = require('angular');

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_SERVERGROUPADVANCEDSETTINGS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.advancedSetting.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_SERVERGROUPADVANCEDSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_SERVERGROUPADVANCEDSETTINGS_CONTROLLER, [])
  .controller('azureServerGroupAdvancedSettingsCtrl', [
    '$scope',
    'modalWizardService',
    function($scope, modalWizardService) {
      modalWizardService.getWizard().markComplete('advanced');

      $scope.$watch('form.$valid', function(newVal) {
        if (newVal) {
          modalWizardService.getWizard().markClean('advanced');
        } else {
          modalWizardService.getWizard().markDirty('advanced');
        }
      });
    },
  ]);
