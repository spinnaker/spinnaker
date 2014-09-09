'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('collapsibleSection', function() {
    return {
      restrict: 'E',
      replace: true,
      transclude: true,
      scope: {
        heading: '@',
        expanded: '@?'
      },
      templateUrl: 'views/collapsibleSection.html',
      link: function(scope) {
        scope.state = {expanded: scope.expanded === 'true'};
        scope.getIcon = function() {
          return scope.state.expanded ? 'down' : 'up';
        };

        scope.toggle = function() {
          scope.state.expanded = !scope.state.expanded;
        };
      }
    };
  });
