'use strict';

angular.module('spinnaker.deploymentStrategy')
  .directive('deploymentStrategySelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        labelColumns: '@',
        fieldColumns: '@',
      },
      templateUrl: 'scripts/modules/deploymentStrategy/deploymentStrategySelector.html',
      controller: 'DeploymentStrategySelectorCtrl',
      controllerAs: 'strategyCtrl'
    };
  });
