'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.instances.directive', [
  require('../utils/jQuery.js'),
])
  .directive('instances', function ($rootScope, $timeout, $) {
    return {
      restrict: 'E',
      scope: {
        instances: '=',
        highlight: '=',
      },
      link: function (scope, elem) {
        var $state = $rootScope.$state;
        let tooltipTargets = [];

        var base = elem.parent().inheritedData('$uiView').state;

        let removeTooltips = () => {
          tooltipTargets.forEach(target => $(target).tooltip('destroy'));
          tooltipTargets.length = 0;
        };

        function renderInstances() {
          scope.activeInstance = null;
          var instances = _.sortBy(scope.instances, 'launchTime');
          let innerHtml = '<div class="instances">' + instances.map(function(instance) {
              var id = instance.id,
                activeClass = '';
              var params = {instanceId: instance.id, provider: instance.provider };
              if ($state.includes('**.instanceDetails', params)) {
                activeClass = ' active';
                scope.activeInstance = params;
              }
              if (scope.highlight === id) {
                activeClass += ' highlighted';
              }

              return '<a title="' + id +
                '" data-provider="' + instance.provider +
                '" data-toggle="tooltip" data-instance-id="' + id +
                '" class="instance health-status-' + instance.healthState + activeClass + '"></a>';
            }).join('') + '</div>';

          if (innerHtml !== elem.get(0).innerHTML) {
            removeTooltips();
            elem.get(0).innerHTML = innerHtml;
          }
        }

        elem.click(function(event) {
          $timeout(function() {
            if (event.target && event.target.getAttribute('data-instance-id')) {
              // anything handled by ui-sref or actual links should be ignored
              if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
                return;
              }
              if (scope.activeInstance) {
                $('a[data-instance-id="' + scope.activeInstance.instanceId + '"]', elem).removeClass('active');
              }
              var params = {
                instanceId: event.target.getAttribute('data-instance-id'),
                provider: event.target.getAttribute('data-provider')
              };
              scope.activeInstance = params;
              // also stolen from uiSref directive
              $state.go('.instanceDetails', params, {relative: base, inherit: true});
              event.target.className += ' active';
              event.preventDefault();
            }
          });
        });

        elem.mouseover((event) => {
          if (!tooltipTargets.includes(event.target) && event.target.hasAttribute('data-toggle')) {
            $(event.target).tooltip({animation: false}).tooltip('show');
            tooltipTargets.push(event.target);
          }
        });

        function clearActiveState() {
          if (scope.activeInstance && !$state.includes('**.instanceDetails', scope.activeInstance)) {
            $('a[data-instance-id="' + scope.activeInstance.instanceId + '"]', elem).removeClass('active');
            scope.activeInstance = null;
          }
        }

        scope.$on('$locationChangeSuccess', clearActiveState);

        scope.$on('$destroy', function() {
          removeTooltips();
          elem.unbind('mouseover');
          elem.unbind('click');
        });

        scope.$watch('instances', renderInstances);
        scope.$watch('highlight', renderInstances);
      }
    };
});
