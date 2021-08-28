'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_HEALTHSETTINGSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.healthSettings.selector.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_HEALTHSETTINGSSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_HEALTHSETTINGSSELECTOR_DIRECTIVE, []).directive(
  'azureServerGroupHealthSettingsSelector',
  [
    'azureServerGroupConfigurationService',
    function () {
      return {
        restrict: 'E',
        templateUrl: require('./healthSettingsSelector.directive.html'),
        scope: {
          command: '=',
        },
      };
    },
  ],
);
