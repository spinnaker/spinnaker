'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.deploymentStrategySelector', [
])
  .directive('deploymentStrategySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        labelColumns: '@',
        fieldColumns: '@',
      },
      templateUrl: require('./deploymentStrategySelector.directive.html'),
      controller: 'DeploymentStrategySelectorCtrl',
      controllerAs: 'strategyCtrl'
    };
  });
