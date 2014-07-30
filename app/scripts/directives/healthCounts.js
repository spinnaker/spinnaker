'use strict';

angular.module('deckApp')
  .directive('healthCounts', function (_) {
    return {
      templateUrl: 'views/application/healthCounts.html',
      restrict: 'E',
      replace: true,
      scope: {
        serverGroup: '='
      },
      link: function(scope) {
        scope.serverGroup.upCount = _.filter(scope.serverGroup.asg.instances, {healthStatus: 'Healthy'}).length;
        scope.serverGroup.downCount = _.filter(scope.serverGroup.asg.instances, {healthStatus: 'Unhealthy'}).length;
        scope.serverGroup.unknownCount = _.filter(scope.serverGroup.asg.instances, {healthStatus: 'Unknown'}).length;
      }
    };
  });
