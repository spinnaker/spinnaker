'use strict';


angular.module('deckApp')
  .directive('healthCounts', function () {
    return {
      templateUrl: 'views/application/healthCounts.html',
      restrict: 'E',
      replace: true,
      scope: {
        container: '='
      },
      link: function(scope) {
        var container = scope.container,
            up = container.upCount,
            down = container.downCount,
            unknown = container.unknownCount,
            total = up + down + unknown;

        scope.healthPercent = total ? parseInt(up*100/total) : 'n/a';
        scope.healthPercentLabel = total ? '%' : '';
      }
    };
  }
);
