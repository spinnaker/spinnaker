'use strict';

import { module } from 'angular';

export const CORE_CLUSTER_FILTER_COLLAPSIBLEFILTERSECTION_DIRECTIVE = 'cluster.filter.collapse';
export const name = CORE_CLUSTER_FILTER_COLLAPSIBLEFILTERSECTION_DIRECTIVE; // for backwards compatibility
module(CORE_CLUSTER_FILTER_COLLAPSIBLEFILTERSECTION_DIRECTIVE, []).directive('filterSection', function () {
  return {
    restrict: 'E',
    transclude: true,
    scope: {
      heading: '@',
      expanded: '@?',
      helpKey: '@?',
    },
    templateUrl: require('./collapsibleFilterSection.html'),
    link: function (scope) {
      const expanded = scope.expanded === 'true';
      scope.state = { expanded: expanded };
      scope.getIconStyle = function () {
        return scope.state.expanded
          ? 'transform: rotate(90deg); transition: all 0.15s ease'
          : 'transform: rotate(0deg); transition: all 0.15s ease';
      };

      scope.toggle = function () {
        scope.state.expanded = !scope.state.expanded;
      };
    },
  };
});
