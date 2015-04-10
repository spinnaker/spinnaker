'use strict';


angular.module('deckApp.healthCounts.directive', [])
  .directive('healthCounts', function ($templateCache) {
    return {
      templateUrl: 'scripts/modules/healthCounts/healthCounts.html',
      restrict: 'E',
      scope: {
        container: '='
      },
      link: function(scope) {

        var template = $templateCache.get('views/directives/healthLegend.html');
        scope.legend = template;

        function calculateHealthPercent() {
          var container = scope.container,
            up = container.upCount || 0,
            down = container.downCount || 0,
            unknown = container.unknownCount || 0,
            total = up + down + unknown,
            healthPercent = total ? parseInt(up*100/total) : 'n/a';

          scope.healthPercent = healthPercent;
          scope.healthPercentLabel = total ? '%' : '';
          scope.healthStatus = healthPercent === 100 ? 'healthy'
            : healthPercent < 100 && healthPercent > 0 ? 'unhealthy'
            : healthPercent === 0 ? 'dead' : 'disabled';

        }

        scope.$watch('container', calculateHealthPercent);
      }
    };
  }
);
