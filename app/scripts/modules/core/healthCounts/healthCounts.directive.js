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
            up = container.up || 0,
            down = container.down || 0,
            succeeded = container.succeeded || 0,
            failed = container.failed || 0,
            unknown = container.unknown || 0,
            starting = container.starting || 0,
            total = container.total || up + down + unknown + starting + succeeded + failed,
            healthPercent = total ? parseInt((up + succeeded) * 100 / total) : 'n/a';

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
