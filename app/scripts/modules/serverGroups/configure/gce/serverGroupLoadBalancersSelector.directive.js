'use strict';

angular.module('deckApp.serverGroup.configure.gce')
  .directive('gceServerGroupLoadBalancersSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/gce/serverGroupLoadBalancersDirective.html'
    };
  });
