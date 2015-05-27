'use strict';

angular.module('spinnaker.serverGroup.configure.aws')
  .directive('awsServerGroupSecurityGroupsSelector', function(awsServerGroupConfigurationService, infrastructureCaches) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupSecurityGroupsDirective.html',
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
  });
