'use strict';

const angular = require('angular');

module.exports = angular.module('cluster.filter.collapse', []).directive('filterSection', function() {
  return {
    restrict: 'E',
    transclude: true,
    scope: {
      heading: '@',
      expanded: '@?',
      helpKey: '@?',
    },
    templateUrl: require('./collapsibleFilterSection.html'),
    link: function(scope) {
      var expanded = scope.expanded === 'true';
      scope.state = { expanded: expanded };
      scope.getIconStyle = function() {
        return scope.state.expanded
          ? 'transform: rotate(90deg); transition: all 0.15s ease'
          : 'transform: rotate(0deg); transition: all 0.15s ease';
      };

      scope.toggle = function() {
        scope.state.expanded = !scope.state.expanded;
      };
    },
  };
});
