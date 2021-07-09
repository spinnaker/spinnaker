'use strict';

import * as angular from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.advancedSettings.selector.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGSSELECTOR_DIRECTIVE, [])
  .directive('azureServerGroupAdvancedSettingsSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./advancedSettingsSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'adv',
      controller: 'azureServerGroupAdvancedSettingsSelectorCtrl',
    };
  })
  .controller('azureServerGroupAdvancedSettingsSelectorCtrl', function () {
    this.addDataDisk = () => {
      const newDataDisks = angular.copy(this.command.dataDisks);
      this.command.dataDisks = newDataDisks.concat([
        {
          lun: 0,
          managedDisk: {
            storageAccountType: 'Standard_LRS',
          },
          diskSizeGB: 1,
          caching: 'None',
          createOption: 'Empty',
        },
      ]);
    };

    this.removeDataDisk = (index) => {
      const newDataDisks = angular.copy(this.command.dataDisks);
      newDataDisks.splice(index, 1);
      this.command.dataDisks = newDataDisks;
    };
  });
