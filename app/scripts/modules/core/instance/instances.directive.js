'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.instances.directive', [
  require('../utils/jQuery.js'),
  require('../utils/lodash.js'),
])
  .directive('instances', function ($timeout, $, _) {
    return {
      restrict: 'E',
      scope: {
        instances: '=',
        highlight: '=',
      },
      link: function (scope, elem) {
        var $state = scope.$parent.$state;
        let lastRender = '',
            tooltipsApplied = false;

        var base = elem.parent().inheritedData('$uiView').state;

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

          if (innerHtml !== lastRender) {
            if (tooltipsApplied) {
              $('[data-toggle="tooltip"]', elem).tooltip('destroy');
              tooltipsApplied = false;
            }
            elem.get(0).innerHTML = innerHtml;
            lastRender = innerHtml;
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
                $('a[data-instance-id="' + scope.activeInstance.instanceId+'"]', elem).removeClass('active');
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
          $(event.target, elem).tooltip({placement: 'top', container: 'body', animation: false}).tooltip('show');
          tooltipsApplied = true;
        });

        function clearActiveState() {
          if (scope.activeInstance && !$state.includes('**.instanceDetails', scope.activeInstance)) {
            $('a[data-instance-id="' + scope.activeInstance.instanceId+'"]', elem).removeClass('active');
            scope.activeInstance = null;
          }
        }

        scope.$on('$locationChangeSuccess', clearActiveState);

        scope.$on('$destroy', function() {
          if (tooltipsApplied) {
            $('[data-toggle="tooltip"]', elem).tooltip('destroy');
            tooltipsApplied = false;
          }
          elem.unbind('mouseover');
          elem.unbind('click');
          lastRender = null;
        });

        scope.$watch('instances', renderInstances);
        scope.$watch('highlight', renderInstances);
      }
    };
});
