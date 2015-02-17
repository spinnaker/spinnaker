'use strict';


angular.module('deckApp')
  .directive('instanceList', function (scrollTriggerService, clusterFilterService, $rootScope, $timeout) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/instance/instanceList.html',
      scope: {
        instances: '=',
        renderInstancesOnScroll: '=',
        scrollTarget: '@',
        sortFilter: '=',
      },
      link: function (scope, elem) {
        scope.$state = scope.$parent.$state;
        scope.rendered = false;

        scope.viewModel = {
          instances: [],
          hasDiscovery: true,
          hasLoadBalancers: true,
        };

        function showAllInstances() {
          scope.$evalAsync(function() {
            buildViewModel();
            scope.rendered = true;
          });
        }

        if (scope.renderInstancesOnScroll) {
          scrollTriggerService.register(scope, elem, scope.scrollTarget, showAllInstances);
        } else {
          showAllInstances();
        }

        function buildViewModel() {
          var instances = [],
            hasDiscovery = false, hasLoadBalancers = false;

          scope.instances.forEach(function(instance) {
            if (!clusterFilterService.shouldShowInstance(instance)) {
              return;
            }
            var instanceView = {
              id: instance.id,
              launchTime: instance.launchTime,
              availabilityZone: instance.availabilityZone,
              status: instance.isHealthy ? 'up' : instance.hasHealthStatus ? 'down' : 'unknown',
            };
            instances.push(instanceView);
            instance.health.forEach(function(health) {
              if (health.type === 'LoadBalancer') {
                hasLoadBalancers = true;
                instanceView.loadBalancers = health.loadBalancers;
                instanceView.loadBalancerSort =  _(health.loadBalancers)
                  .sortByAll(['name', 'state'])
                  .map(function(lbh) { return lbh.name + ':' + lbh.state; })
                  .join(',');
              }
              if (health.type === 'Discovery') {
                hasDiscovery = true;
                instanceView.discoveryState = health.state.toLowerCase();
                instanceView.discoveryStatus = health.status.toLowerCase();
              }
            });
          });

          scope.viewModel = {
            instances: instances,
            hasDiscovery: hasDiscovery,
            hasLoadBalancers: hasLoadBalancers
          };
        }

        // stolen from uiSref directive
        var base = elem.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        scope.loadInstanceDetails = function(event, id) {
          $timeout(function() {
            // anything handled by ui-sref or actual links should be ignored
            if (event.isDefaultPrevented() || (event.originalEvent && event.originalEvent.target.href)) {
              return;
            }
            event.preventDefault();
            var params = {
              instanceId: id
            };
            // also stolen from uiSref directive
            scope.$state.go('.instanceDetails', params, {relative: base, inherit: true});
          });
        };

        scope.updateQueryParams = clusterFilterService.updateQueryParams;

      }
    };
  }
);
