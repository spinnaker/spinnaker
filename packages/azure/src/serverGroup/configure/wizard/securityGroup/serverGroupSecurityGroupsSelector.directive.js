'use strict';

import { module } from 'angular';

import { FirewallLabels, InfrastructureCaches } from '@spinnaker/core';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.securityGroupSelector.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPSSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUP_SERVERGROUPSECURITYGROUPSSELECTOR_DIRECTIVE, []).directive(
  'azureServerGroupSecurityGroupsSelector',
  [
    'azureServerGroupConfigurationService',
    function (azureServerGroupConfigurationService) {
      return {
        restrict: 'E',
        scope: {
          command: '=',
        },
        templateUrl: require('./serverGroupSecurityGroupsSelector.directive.html'),
        link: function (scope) {
          scope.firewallLabel = FirewallLabels.get('firewall');

          scope.getSecurityGroupRefreshTime = function () {
            return InfrastructureCaches.get('securityGroups').getStats().ageMax;
          };

          scope.refreshSecurityGroups = function () {
            scope.refreshing = true;
            azureServerGroupConfigurationService.refreshSecurityGroups(scope.command).then(function () {
              scope.refreshing = false;
            });
          };
        },
      };
    },
  ],
);
