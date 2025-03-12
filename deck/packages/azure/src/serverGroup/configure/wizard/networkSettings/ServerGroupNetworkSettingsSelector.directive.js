'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.networkSettings.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE, []).directive(
  'azureServerGroupNetworkSettingsSelector',
  function () {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./ServerGroupNetworkSettingsSelector.directive.html'),
    };
  },
);
