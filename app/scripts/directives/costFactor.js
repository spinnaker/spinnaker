'use strict';

angular.module('deckApp')
  .directive('costFactor', function() {
    return {
      restrict: 'E',
      scope: {
        factor: '=',
        range: '='
      },
      templateUrl: 'views/directives/costFactor.html',
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
            console.warn('usage min:', scope.min);
          }
        }
        applyFactors();
        scope.$watch('range', applyFactors);

      }
    };
  });
