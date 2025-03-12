'use strict';

import { module } from 'angular';

import { WaypointService } from './waypoint.service';

export const CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE = 'spinnaker.core.utils.waypoints.container.directive';
export const name = CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE; // for backwards compatibility
module(CORE_UTILS_WAYPOINTS_WAYPOINTCONTAINER_DIRECTIVE, []).directive('waypointContainer', function () {
  return {
    restrict: 'A',
    scope: {
      key: '@waypointContainerKey',
      offsetHeight: '=waypointOffset',
    },
    link: {
      post: function (scope, elem) {
        const offset = scope.offsetHeight || 0;
        WaypointService.registerWaypointContainer(scope, elem, scope.key, offset);
      },
    },
  };
});
