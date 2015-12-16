'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.securityGroupSelector.directive', [])
  .directive('azureServerGroupSecurityGroupsSelector', function(azureServerGroupConfigurationService, infrastructureCaches) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupSecurityGroupsSelector.directive.html'),
      link: function(scope) {

        scope.getSecurityGroupRefreshTime = function() {
          return infrastructureCaches.securityGroups.getStats().ageMax;
        };

        scope.refreshSecurityGroups = function() {
          scope.refreshing = true;
          azureServerGroupConfigurationService.refreshSecurityGroups(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      }
    };
  });
