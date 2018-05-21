'use strict';

import { WaypointService } from './waypoint.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.utils.waypoints.container.directive', [])
  .directive('waypointContainer', function() {
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
