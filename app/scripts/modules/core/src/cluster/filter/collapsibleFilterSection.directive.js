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
      scope.getIcon = function() {
        return scope.state.expanded ? 'down' : 'right';
      };

      scope.toggle = function() {
        scope.state.expanded = !scope.state.expanded;
      };
    },
  };
});
