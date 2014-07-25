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
        scope.serverGroup.upCount = _.filter(scope.serverGroup.instances, {isHealthy: true}).length;
      }
    };
  });
