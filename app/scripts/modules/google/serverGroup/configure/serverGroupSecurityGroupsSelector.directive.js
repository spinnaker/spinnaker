'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.securityGroupSelector.directive', [])
  .directive('gceServerGroupSecurityGroupsSelector', function(gceServerGroupConfigurationService, infrastructureCaches) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupSecurityGroupsDirective.html'),
      link: function(scope) {

        scope.getSecurityGroupRefreshTime = function() {
          return infrastructureCaches.securityGroups.getStats().ageMax;
        };

        scope.refreshSecurityGroups = function() {
          scope.refreshing = true;
          gceServerGroupConfigurationService.refreshSecurityGroups(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      }
    };
  });
