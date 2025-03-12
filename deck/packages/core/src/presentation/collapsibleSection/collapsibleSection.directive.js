'use strict';

import { module } from 'angular';

import { CollapsibleSectionStateCache } from '../../cache';

export const CORE_PRESENTATION_COLLAPSIBLESECTION_COLLAPSIBLESECTION_DIRECTIVE =
  'spinnaker.core.presentation.collapsibleSection.directive';
export const name = CORE_PRESENTATION_COLLAPSIBLESECTION_COLLAPSIBLESECTION_DIRECTIVE; // for backwards compatibility
module(CORE_PRESENTATION_COLLAPSIBLESECTION_COLLAPSIBLESECTION_DIRECTIVE, []).directive(
  'collapsibleSection',
  function () {
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
      link: function (scope) {
        const cacheKey = scope.cacheKey || scope.heading;
        let expanded = true;
        if (cacheKey) {
          expanded = CollapsibleSectionStateCache.isSet(cacheKey)
            ? CollapsibleSectionStateCache.isExpanded(cacheKey)
            : scope.expanded === 'true';
        }
        scope.state = { expanded: expanded };
        scope.getIconStyle = function () {
          return scope.state.expanded
            ? 'transform: rotate(90deg); transition: all 0.15s ease'
            : 'transform: rotate(0deg); transition: all 0.15s ease';
        };

        scope.getClassType = function () {
          return scope.subsection ? 'sub' : '';
        };

        scope.toggle = function () {
          scope.state.expanded = !scope.state.expanded;
          if (cacheKey) {
            CollapsibleSectionStateCache.setExpanded(cacheKey, scope.state.expanded);
          }
        };
      },
    };
  },
);
