'use strict';


angular.module('spinnaker')
  .directive('collapsibleSection', function(collapsibleSectionStateCache) {
    return {
      restrict: 'E',
      replace: true,
      transclude: true,
      scope: {
        heading: '@',
        expanded: '@?',
        bodyClass: '@?'
      },
      templateUrl: 'views/collapsibleSection.html',
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
