'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_SERVERGROUPHEALTHSETTINGS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.healthSetting.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_SERVERGROUPHEALTHSETTINGS_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_HEALTHSETTINGS_SERVERGROUPHEALTHSETTINGS_CONTROLLER, []).controller(
  'azureServerGroupHealthSettingsCtrl',
  [
    '$scope',
    function ($scope) {
      if (typeof $scope.command.healthSettings === 'undefined') {
        $scope.command.healthSettings = {};
      }

      this.healthCheckProtocols = [
        { displayName: 'N/A', name: null },
        { displayName: 'HTTP', name: 'http' },
        { displayName: 'TCP', name: 'tcp' },
      ];

      this.requiresHealthCheckPath = function () {
        return $scope.command.healthSettings.protocol === 'http';
      };

      this.changeHealthCheckProtocol = function (newProtocol) {
        if (newProtocol == null) {
          $scope.command.healthSettings.protocol = null;
          $scope.command.healthSettings.port = null;
          $scope.command.healthSettings.requestPath = null;
        } else if (!this.requiresHealthCheckPath()) {
          $scope.command.healthSettings.requestPath = null;
        }
      };
    },
  ],
);
