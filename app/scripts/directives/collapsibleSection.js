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
        scope.expanded = scope.expanded !== 'false';
        scope.getIcon = function() {
          return scope.expanded ? 'down' : 'up';
        };

        scope.toggle = function() {
          scope.expanded = !scope.expanded;
        };
      }
    };
  });
