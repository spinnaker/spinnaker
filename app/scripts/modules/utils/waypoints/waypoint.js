'use strict';

angular.module('deckApp.utils.waypoints.container.directive', [
  'deckApp.utils.waypoints.service',

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
