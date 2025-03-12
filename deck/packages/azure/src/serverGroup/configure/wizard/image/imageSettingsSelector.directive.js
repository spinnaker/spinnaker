'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_IMAGESETTINGS_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.imageSettingsSelector.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_IMAGESETTINGS_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_IMAGESETTINGS_IMAGESETTINGS_DIRECTIVE, []).directive(
  'azureServerGroupImageSettingsSelector',
  [
    'azureServerGroupConfigurationService',
    function () {
      return {
        restrict: 'E',
        templateUrl: require('./imageSettingsSelector.directive.html'),
        scope: {
          command: '=',
        },
      };
    },
  ],
);
