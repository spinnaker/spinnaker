'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.presentation.collapsibleSection.directive', [
  require('../../cache/collapsibleSectionStateCache.js')
])
  .directive('collapsibleSection', function(collapsibleSectionStateCache) {
    return {
      restrict: 'E',
      replace: true,
      transclude: true,
      scope: {
        heading: '@',
        expanded: '@?',
        bodyClass: '@?',
        helpKey: '@'
      },
      templateUrl: require('./collapsibleSection.directive.html'),
      link: function(scope) {
        var expanded = collapsibleSectionStateCache.isSet(scope.heading) ?
          collapsibleSectionStateCache.isExpanded(scope.heading) :
          scope.expanded === 'true';
        scope.state = {expanded: expanded};
        scope.getIcon = function() {
          return scope.state.expanded ? 'down' : 'up';
        };

        scope.toggle = function() {
          scope.state.expanded = !scope.state.expanded;
          collapsibleSectionStateCache.setExpanded(scope.heading, scope.state.expanded);
        };
      }
    };
});
