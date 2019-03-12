'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.loadBalancer.directive', [])
  .directive('azureServerGroupLoadBalancersSelector', [
    'azureServerGroupConfigurationService',
    function() {
      return {
        restrict: 'E',
        scope: {
          command: '=',
        },
        templateUrl: require('./serverGroupLoadBalancersSelector.directive.html'),
      };
    },
  ]);
