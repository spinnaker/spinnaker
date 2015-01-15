'use strict';

angular.module('deckApp.serverGroup.configure.common')
  .directive('costFactor', function() {
    return {
      restrict: 'E',
      scope: {
        factor: '=',
        range: '='
      },
      templateUrl: 'scripts/modules/serverGroups/configure/common/costFactor.html',
      link: function(scope) {
        function getUsage(factor) {
          return {
            used: new Array(factor + 1).join('$'),
            unused: new Array(5 - factor).join('$')
          };
        }
        function applyFactors() {
          if (!scope.range) {
            var usage = getUsage(scope.factor);
            scope.used = usage.used;
            scope.unused = usage.unused;
          } else {
            scope.min = getUsage(scope.range.min);
            scope.max = getUsage(scope.range.max);
          }
        }
        applyFactors();
        scope.$watch('range', applyFactors);

      }
    };
  });
