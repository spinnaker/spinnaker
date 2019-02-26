'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.loadBalancer.directive', [])
  .directive('azureServerGroupLoadBalancersSelector', [
    'azureServerGroupConfigurationService',
    function(azureServerGroupConfigurationService) {
      return {
        restrict: 'E',
        scope: {
          command: '=',
        },
        templateUrl: require('./serverGroupLoadBalancersSelector.directive.html'),
        link: function(scope) {
          scope.getLoadBalancerRefreshTime = function() {
            return InfrastructureCaches.get('loadBalancers').getStats().ageMax;
          };

          scope.refreshLoadBalancers = function() {
            scope.refreshing = true;
            azureServerGroupConfigurationService.refreshLoadBalancers(scope.command).then(function() {
              scope.refreshing = false;
            });
          };
        },
      };
    },
  ]);
