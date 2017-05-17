'use strict';

import { WAYPOINT_SERVICE } from './waypoint.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.waypoints.container.directive', [
  WAYPOINT_SERVICE,
])
  .directive('waypointContainer', function (waypointService) {
    return {
      restrict: 'A',
      scope: {
        key: '@waypointContainerKey',
        offsetHeight: '=waypointOffset',
      },
      link: {
        post: function (scope, elem) {
          var offset = scope.offsetHeight || 0;
          waypointService.registerWaypointContainer(scope, elem, scope.key, offset);
        }
      }
    };
  });
