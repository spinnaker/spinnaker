'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.securityGroupSelector.directive', [])
  .directive('azureServerGroupSecurityGroupsSelector', function(azureServerGroupConfigurationService) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupSecurityGroupsSelector.directive.html'),
      link: function(scope) {
        scope.getSecurityGroupRefreshTime = function() {
          return InfrastructureCaches.get('securityGroups').getStats().ageMax;
        };

        scope.refreshSecurityGroups = function() {
          scope.refreshing = true;
          azureServerGroupConfigurationService.refreshSecurityGroups(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      },
    };
  });
