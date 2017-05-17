'use strict';

import { WAYPOINT_SERVICE } from './waypoint.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.waypoints.container.directive', [
  WAYPOINT_SERVICE
])
  .directive('waypoint', function () {
    return {
      restrict: 'A',
      link: {
        post: function (scope, elem) {
          scope.$on('$destroy', function() {
            elem.removeData();
          });
        }
      }
    };
  });
