'use strict';

import { WaypointService } from './waypoint.service';

const angular = require('angular');

export const CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE = 'spinnaker.core.utils.waypoints.container.directive';
export const name = CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE; // for backwards compatibility
angular.module(CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE, []).directive('waypointContainer', function() {
  return {
    restrict: 'A',
    scope: {
      key: '@waypointContainerKey',
      offsetHeight: '=waypointOffset',
    },
    link: {
      post: function(scope, elem) {
        var offset = scope.offsetHeight || 0;
        WaypointService.registerWaypointContainer(scope, elem, scope.key, offset);
      },
    },
  };
});
