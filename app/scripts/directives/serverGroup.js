'use strict';


angular.module('deckApp')
  .directive('serverGroup', function ($rootScope, $timeout, $filter, scrollTriggerService, _, waypointService) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/cluster/serverGroup.html',
      scope: {
        cluster: '=',
        serverGroup: '=',
        displayOptions: '=',
        sortFilter: '=',
        application: '=',
        parentHeading: '=',
      },
      link: function (scope, el) {
        // stolen from uiSref directive
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        function loadView() {
          scope.$evalAsync(function() {
            scope.viewModel.loaded = true;
            scope.viewModel.placeholderStyle = '';
          });
        }

        function calculatePlaceholderHeight() {
          if (scope.displayOptions.showInstances) {
            if (scope.displayOptions.listInstances) {
              var extraLoadBalancers = scope.serverGroup.instances.reduce(function(acc, instance) {
                var loadBalancerHealths = _.find(instance.health, { type: 'LoadBalancer' });
                return loadBalancerHealths ? loadBalancerHealths.loadBalancers.length - 1 : 0 + acc;
              }, 0);
              return scope.serverGroup.instances.length * 29 + extraLoadBalancers * 18 + 34; //34 = header (29) + original padding (5)
            } else {
              var rows = Math.ceil(scope.serverGroup.instances.length / 60);
              return rows * 20 + 5;
            }
          }
        }

        var serverGroup = scope.serverGroup;

        scope.viewModel = {
          loaded: true,
          waypoint: [serverGroup.account,serverGroup.region,serverGroup.name].join(':'),
          serverGroup: serverGroup,
          serverGroupSequence: $filter('serverGroupSequence')(serverGroup.name),
          jenkins: null
        };

        if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins && serverGroup.buildInfo.jenkins.host) {
          var jenkins = serverGroup.buildInfo.jenkins;
          scope.viewModel.jenkins = {
            href: [jenkins.host + 'job', jenkins.name, jenkins.number, ''].join('/'),
            number: jenkins.number,
          };
        }

        var wasInLastWindow = waypointService.getLastWindow(scope.application.name).indexOf(scope.viewModel.waypoint) !== -1;

        if (scope.displayOptions.renderInstancesOnScroll && !wasInLastWindow) {
          scope.viewModel.loaded = false;
          scope.viewModel.placeholderStyle = { 'padding-bottom': calculatePlaceholderHeight() + 'px'};
          scrollTriggerService.register(scope, el, 'clusters-content', loadView);
        }

        scope.loadDetails = function(e) {
          $timeout(function() {
            var serverGroup = scope.serverGroup;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && (e.originalEvent.defaultPrevented || e.originalEvent.target.href))) {
              return;
            }
            var params = {
              region: serverGroup.region,
              accountId: serverGroup.account,
              serverGroup: serverGroup.name,
              provider: serverGroup.type,
            };
            // also stolen from uiSref directive
            scope.$state.go('.serverGroup', params, {relative: base, inherit: true});
          });
        };

        if (scope.serverGroup.isDisabled) {
          scope.serverGroup.instances.forEach(function(instance) {
            instance.healthStatus = 'Disabled';
          });
        }

        scope.headerIsSticky = function() {
          if (!scope.displayOptions.showInstances) {
            return false;
          }
          return scope.serverGroup.instances.length;
        };
      }
    };
  }
);
