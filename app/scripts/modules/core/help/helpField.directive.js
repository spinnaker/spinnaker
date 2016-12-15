'use strict';
import {HELP_CONTENTS_REGISTRY} from './helpContents.registry';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.help.helpField.directive', [
    require('./helpContents.js'),
    HELP_CONTENTS_REGISTRY,
    require('angulartics'),
  ])
  .directive('helpField', function (helpContents, helpContentsRegistry, $analytics) {
    return {
      restrict: 'E',
      templateUrl: require('./helpField.directive.html'),
      scope: {
        key: '@',
        fallback: '@',
        content: '@',
        placement: '@',
        expand: '=',
        trigger: '@'
      },
      link: {
        pre: function (scope) {
          function applyContents() {
            if (!scope.content && scope.key) {
              scope.content = helpContentsRegistry.getHelpField(scope.key) || helpContents[scope.key] || scope.fallback;
            }
            scope.trigger = scope.trigger || 'mouseenter focus';
            scope.contents = {
              content: scope.content,
              placement: scope.placement || 'top',
            };
          }
          applyContents();

          scope.$watch('key', applyContents);
          scope.$watch('fallback', applyContents);
          scope.$watch('content', applyContents);

          let tooltipShownStart = null;

          scope.tooltipShown = () => {
            tooltipShownStart = new Date().getTime();
          };

          scope.tooltipHidden = () => {
            let end = new Date().getTime();
            // only track the event if the tooltip was on the screen for a little while, i.e. it wasn't accidentally
            // moused over
            if (end - tooltipShownStart > 500) {
              $analytics.eventTrack('Help contents viewed', {category: 'Help', label: scope.key || scope.content});
            }
            tooltipShownStart = null;
          };
        }
      }
    };
  });
