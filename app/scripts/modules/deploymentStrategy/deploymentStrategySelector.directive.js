'use strict';

let angular = require('angular');

require('./deploymentStrategySelector.html');

module.exports = angular.module('spinnaker.deploymentStrategy.deploymentStrategySelector', [
])
  .directive('deploymentStrategySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        labelColumns: '@',
        fieldColumns: '@',
      },
      templateUrl: require('./deploymentStrategySelector.html'),
      controller: 'DeploymentStrategySelectorCtrl',
      controllerAs: 'strategyCtrl'
    };
  }).name;
