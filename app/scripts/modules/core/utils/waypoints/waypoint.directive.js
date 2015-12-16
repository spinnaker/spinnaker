'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.waypoints.container.directive', [
  require('./waypoint.service.js'),

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
