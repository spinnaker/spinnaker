'use strict';

angular.module('deckApp.serverGroup.configure.aws')
  .directive('awsServerGroupLoadBalancersSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupLoadBalancersDirective.html'
    };
  });
