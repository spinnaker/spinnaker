'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.directive', [
  require('../utils/lodash.js'),
])
  .directive('loadBalancer', function ($rootScope, $timeout, _, LoadBalancerFilterModel) {
    return {
      restrict: 'E',
      templateUrl: require('./loadBalancer/loadBalancer.html'),
      scope: {
        application: '=',
        loadBalancer: '=',
        serverGroups: '=',
      },
      link: function(scope, el) {
        var base = el.parent().inheritedData('$uiView').state;
        var loadBalancer = scope.loadBalancer;

        scope.sortFilter = LoadBalancerFilterModel.sortFilter;
        scope.$state = $rootScope.$state;

        scope.waypoint = [loadBalancer.account, loadBalancer.region, loadBalancer.name].join(':');

        scope.viewModel = {
          instances: loadBalancer.instances.concat(_.flatten(_.pluck(loadBalancer.serverGroups, 'detachedInstances')))
        };

        scope.loadDetails = function(event) {
          $timeout(function() {
            var loadBalancer = scope.loadBalancer;
            // anything handled by ui-sref or actual links should be ignored
            if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || event.originalEvent.target.href))) {
              return;
            }
            var params = {
              application: scope.application.name,
              region: loadBalancer.region,
              accountId: loadBalancer.account,
              name: loadBalancer.name,
              vpcId: loadBalancer.vpcId,
              provider: loadBalancer.provider,
            };

            if (angular.equals(scope.$state.params, params)) {
              // already there
              return;
            }
            // also stolen from uiSref directive
            scope.$state.go('.loadBalancerDetails', params, {relative: base, inherit: true});
          });
        };
      }
    };
  });
