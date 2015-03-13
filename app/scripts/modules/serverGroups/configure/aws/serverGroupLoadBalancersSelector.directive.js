'use strict';

angular.module('deckApp.serverGroup.configure.aws')
  .directive('awsServerGroupLoadBalancersSelector', function(awsServerGroupConfigurationService, infrastructureCaches) {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupLoadBalancersDirective.html',
      link: function(scope) {

        scope.getLoadBalancerRefreshTime = function() {
          return infrastructureCaches.loadBalancers.getStats().ageMax;
        };

        scope.refreshLoadBalancers = function() {
          scope.refreshing = true;
          awsServerGroupConfigurationService.refreshLoadBalancers(scope.command).then(function() {
            scope.refreshing = false;
          });
        };
      }
    };
  });
