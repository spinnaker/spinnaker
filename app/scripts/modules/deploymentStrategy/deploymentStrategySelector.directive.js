'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deploymentStrategy')
  .directive('deploymentStrategySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '='
      },
      template: require('./deploymentStrategySelector.html'),
      controller: 'DeploymentStrategySelectorCtrl',
      controllerAs: 'strategyCtrl'
    };
  });
