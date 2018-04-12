'use strict';

const angular = require('angular');

import { CollapsibleSectionStateCache } from 'core/cache';

module.exports = angular
  .module('spinnaker.core.presentation.collapsibleSection.directive', [])
  .directive('collapsibleSection', function() {
    return {
      restrict: 'E',
      replace: true,
      transclude: {
        heading: '?sectionHeading',
        body: '?sectionBody',
      },
      scope: {
        heading: '@',
        expanded: '@?',
        bodyClass: '@?',
        cacheKey: '@?',
        helpKey: '@',
        subsection: '=',
      },
      templateUrl: require('./collapsibleSection.directive.html'),
      link: function(scope) {
        let cacheKey = scope.cacheKey || scope.heading;
        let expanded = true;
        if (cacheKey) {
          expanded = CollapsibleSectionStateCache.isSet(cacheKey)
            ? CollapsibleSectionStateCache.isExpanded(cacheKey)
            : scope.expanded === 'true';
        }
        scope.state = { expanded: expanded };
        scope.getIcon = function() {
          return scope.state.expanded ? 'down' : 'right';
        };

        scope.getClassType = function() {
          return scope.subsection ? 'sub' : '';
        };

        scope.toggle = function() {
          scope.state.expanded = !scope.state.expanded;
          if (cacheKey) {
            CollapsibleSectionStateCache.setExpanded(cacheKey, scope.state.expanded);
          }
        };
      },
    };
  });
