'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.serverGroup', [
  require('./filter/loadBalancer.filter.service.js'),
  require('./filter/loadBalancer.filter.model.js'),
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
