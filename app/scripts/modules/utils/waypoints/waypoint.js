'use strict';

angular.module('spinnaker.utils.waypoints.container.directive', [
  'spinnaker.utils.waypoints.service',

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
