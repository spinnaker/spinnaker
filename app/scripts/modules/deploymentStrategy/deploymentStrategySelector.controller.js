'use strict';

angular.module('deckApp.deploymentStrategy')
  .controller('DeploymentStrategySelectorCtrl', function($scope, deploymentStrategyService) {

    function selectStrategy(newStrategyKey, oldStrategyKey) {
      var oldStrategy = deploymentStrategyService.getStrategy(oldStrategyKey),
          newStrategy = deploymentStrategyService.getStrategy(newStrategyKey);

      if (oldStrategy && oldStrategy.additionalFields) {
        oldStrategy.additionalFields.forEach(function(field) {
          if (!newStrategy || !newStrategy.additionalFields || newStrategy.additionalFields.indexOf(field) === -1) {
            delete $scope.command[field];
          }
        });
      }
      if (newStrategy && newStrategy.additionalFieldsTemplateUrl) {
        $scope.additionalFieldsTemplateUrl = newStrategy.additionalFieldsTemplateUrl;
      } else {
        $scope.additionalFieldsTemplateUrl = null;
      }
    }

    $scope.deploymentStrategies = deploymentStrategyService.listAvailableStrategies();

    $scope.$watch('command.strategy', selectStrategy);

  });
