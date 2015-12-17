'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.instanceList.directive', [
  require('../cluster/filter/clusterFilter.model.js'),
  require('./instanceListBody.directive.js'),
])
  .directive('instanceList', function (ClusterFilterModel) {
    return {
      restrict: 'E',
      templateUrl: require('./instanceList.directive.html'),
      scope: {
        hasDiscovery: '=',
        hasLoadBalancers: '=',
        instances: '=',
        sortFilter: '=',
      },
      link: function (scope) {
        scope.applyParamsToUrl = ClusterFilterModel.applyParamsToUrl;
        scope.showProviderHealth = !scope.hasDiscovery && !scope.hasLoadBalancers;

        scope.columnWidth = {
          id: 14,
          launchTime: 23,
          zone: 13,
          discovery: 16,
          loadBalancers: 34,
          cloudProvider: 34,
        };

        if (!scope.hasDiscovery) {
          scope.columnWidth.id += 4;
          scope.columnWidth.launchTime += 4;
          scope.columnWidth.zone += 4;
          scope.columnWidth.loadBalancers += 4;
        }
      }
    };
  });
