'use strict';

const angular = require('angular');

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.networkSettings.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGSSELECTOR_DIRECTIVE, [])
  .directive('azureServerGroupNetworkSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./ServerGroupNetworkSettingsSelector.directive.html'),
    };
  });
