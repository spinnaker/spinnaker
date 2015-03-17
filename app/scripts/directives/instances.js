'use strict';


angular.module('deckApp')
  .directive('instances', function ($timeout, $) {
    return {
      restrict: 'E',
      scope: {
        instances: '=',
        highlight: '=',
      },
      link: function (scope, elem) {
        var $state = scope.$parent.$state;
        scope.activeInstance = null;

        var base = elem.parent().inheritedData('$uiView').state;

        var instances = _.sortBy(scope.instances, 'launchTime');
        elem.get(0).innerHTML = '<div class="instances">' + instances.map(function(instance) {
          var id = instance.id,
              activeClass = '';
          var params = {instanceId: instance.id, provider: instance.provider };
          if ($state.includes('**.instanceDetails', params)) {
            activeClass = ' active';
            scope.activeInstance = params;
          }

          return '<a title="' + id +
                  '" data-provider="' + instance.provider +
                  '" data-toggle="tooltip" data-instance-id="' + id +
                  '" class="instance health-status-' + instance.healthState + activeClass + '"></a>';
        }).join('') + '</div>';
        $('[data-toggle="tooltip"]', elem).tooltip({placement: 'top', container: 'body'});

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

        function clearActiveState() {
          if (scope.activeInstance && !$state.includes('**.instanceDetails', scope.activeInstance)) {
            $('a[data-instance-id="' + scope.activeInstance.instanceId+'"]', elem).removeClass('active');
            scope.activeInstance = null;
          }
        }

        scope.$on('$locationChangeSuccess', clearActiveState);

        scope.$on('$destroy', function() {
          $('[data-toggle="tooltip"]', elem).tooltip('destroy').removeData();
          elem.unbind('click');
        });
      }
    };
  }
);
