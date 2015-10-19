'use strict';

let angular = require('angular');

require('./healthCounts.less');

module.exports = angular.module('spinnaker.core.healthCounts.directive', [
])
  .directive('healthCounts', function ($templateCache, $sce) {
    return {
      templateUrl: require('./healthCounts.html'),
      restrict: 'E',
      scope: {
        container: '=',
        additionalLegendText: '@',
        legendPlacement: '@',
      },
      link: function(scope) {

        scope.legendPlacement = scope.legendPlacement || 'top';
        scope.legend = $sce.trustAsHtml($templateCache.get(require('./healthLegend.html')));

        if (scope.additionalLegendText) {
          scope.legend += scope.additionalLegendText;
        }

        function calculateHealthPercent() {
          var container = scope.container || {},
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
).name;
