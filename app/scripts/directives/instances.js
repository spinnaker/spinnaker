'use strict';


angular.module('deckApp')
  .directive('instances', function ($timeout, $) {
    return {
      restrict: 'E',
      scope: {
        instances: '=',
        renderInstancesOnScroll: '=',
        highlight: '=',
        scrollTarget: '@',
      },
      link: function (scope, elem) {
        var $state = scope.$parent.$state;
        scope.rendered = true;

        var base = elem.parent().inheritedData('$uiView').state;

        var instances = _.sortBy(scope.instances, 'launchTime');
        elem.get(0).innerHTML = '<div class="instances">' + instances.map(function(instance) {
          var healthStatus = instance.isHealthy ? 'Healthy' : instance.hasHealthStatus ? 'Unhealthy' : 'UnknownStatus',
              disabledClass = instance.healthStatus === 'Disabled' ? ' health-status-Unknown' : ' ',
              activeClass = $state.includes('**.instanceDetails', {instanceId: instance.id, provider: instance.provider }) ? ' active' : ' ',
              id = instance.id;
          return '<a title="' + id +
                  '" data-provider="' + instance.provider +
                  '" data-toggle="tooltip" data-instance-id="' + id +
                  '" class="instance health-status-' + healthStatus + disabledClass + activeClass + '"></a>';
        }).join('') + '</div>';
        $('[data-toggle="tooltip"]', elem).tooltip({placement: 'top', container: 'body'});

        elem.click(function(event) {
          $timeout(function() {
            if (event.target && event.target.getAttribute('data-instance-id')) {
              // anything handled by ui-sref or actual links should be ignored
              if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
                return;
              }
              var params = {
                instanceId: event.target.getAttribute('data-instance-id'),
                provider: event.target.getAttribute('data-provider')
              };
              // also stolen from uiSref directive
              $state.go('.instanceDetails', params, {relative: base, inherit: true});
              $('a.instance').removeClass('active');
              event.target.className += ' active';
              event.preventDefault();
            }
          });
        });

        scope.$on('$destroy', function() {
          $('[data-toggle="tooltip"]', elem).tooltip('destroy');
          elem.unbind('click');
        });
      }
    };
  }
);
