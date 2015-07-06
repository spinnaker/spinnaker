'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws')
  .directive('awsServerGroupSecurityGroupsSelector', function(awsServerGroupConfigurationService, infrastructureCaches) {
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
          awsServerGroupConfigurationService.refreshSecurityGroups(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      }
    };
  }).name;
