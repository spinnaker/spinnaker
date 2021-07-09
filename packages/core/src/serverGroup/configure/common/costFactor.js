'use strict';

import { module } from 'angular';

export const CORE_SERVERGROUP_CONFIGURE_COMMON_COSTFACTOR = 'spinnaker.core.serverGroup.configure.common.costFactor';
export const name = CORE_SERVERGROUP_CONFIGURE_COMMON_COSTFACTOR; // for backwards compatibility
module(CORE_SERVERGROUP_CONFIGURE_COMMON_COSTFACTOR, []).directive('costFactor', function () {
  return {
    restrict: 'E',
    scope: {
      factor: '=',
      range: '=',
    },
    templateUrl: require('./costFactor.html'),
    link: function (scope) {
      function getUsage(factor) {
        return {
          used: new Array(factor + 1).join('$'),
          unused: new Array(5 - factor).join('$'),
        };
      }
      function applyFactors() {
        if (!scope.range) {
          const usage = getUsage(scope.factor);
          scope.used = usage.used;
          scope.unused = usage.unused;
        } else {
          scope.min = getUsage(scope.range.min);
          scope.max = getUsage(scope.range.max);
        }
      }
      applyFactors();
      scope.$watch('range', applyFactors);
    },
  };
});
