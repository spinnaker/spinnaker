'use strict';

let angular = require('angular');

import { LOAD_BALANCER_FILTER_MODEL } from './filter/loadBalancerFilter.model';

module.exports = angular.module('spinnaker.core.loadBalancer.serverGroup', [
  require('./filter/loadBalancer.filter.service.js'),
  LOAD_BALANCER_FILTER_MODEL,
])
  .directive('loadBalancerServerGroup', function ($rootScope, loadBalancerFilterService, LoadBalancerFilterModel) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./loadBalancer/loadBalancerServerGroup.html'),
      scope: {
        loadBalancer: '=',
        serverGroup: '=',
      },
      link: function (scope) {
        scope.$state = $rootScope.$state;
        scope.sortFilter = LoadBalancerFilterModel.sortFilter;
        scope.region = scope.serverGroup.region || scope.loadBalancer.region;

        function setInstances() {
          scope.viewModel = {
            instances: scope.serverGroup.instances.filter(loadBalancerFilterService.shouldShowInstance),
          };
        }

        setInstances();

        scope.$watch('sortFilter', setInstances, true);
      }
    };
  }
);
