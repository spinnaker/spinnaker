'use strict';

const angular = require('angular');

export const CORE_UTILS_WAYPOINTS_WAYPOINT_DIRECTIVE = 'spinnaker.core.utils.waypoints.directive';
export const name = CORE_UTILS_WAYPOINTS_WAYPOINT_DIRECTIVE; // for backwards compatibility
angular.module(CORE_UTILS_WAYPOINTS_WAYPOINT_DIRECTIVE, []).directive('waypoint', function() {
  return {
    restrict: 'A',
    link: {
      post: function(scope, elem) {
        scope.$on('$destroy', function() {
          elem.removeData();
        });
      },
    },
  };
});
