'use strict';


angular.module('deckApp')
  .directive('healthCounts', function ($templateCache) {
    return {
      templateUrl: 'views/application/healthCounts.html',
      restrict: 'E',
      replace: true,
      scope: {
        container: '='
      },
      link: function(scope) {

        var template = $templateCache.get('views/directives/healthLegend.html');
        scope.legend = template;

        function calculateHealthPercent() {
          var container = scope.container,
            up = container.upCount,
            down = container.downCount,
            unknown = container.unknownCount,
            total = up + down + unknown;

          scope.healthPercent = total ? parseInt(up*100/total) : 'n/a';
          scope.healthPercentLabel = total ? '%' : '';
        }

        scope.$watch('container', calculateHealthPercent);
      }
    };
  }
);
