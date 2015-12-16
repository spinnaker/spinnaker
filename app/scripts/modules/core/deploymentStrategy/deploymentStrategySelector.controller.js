'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.deploymentStrategySelectorController', [
  require('./services/deploymentStrategy.service.js')
])
  .controller('DeploymentStrategySelectorCtrl', function($scope, deploymentStrategyService) {

    function selectStrategy(newStrategyKey, oldStrategyKey) {
      var oldStrategy = deploymentStrategyService.getStrategy(oldStrategyKey),
          newStrategy = deploymentStrategyService.getStrategy(newStrategyKey);

      if (oldStrategy && oldStrategy.additionalFields) {
        oldStrategy.additionalFields.forEach(function(field) {
          if (!newStrategy || !newStrategy.additionalFields || newStrategy.additionalFields.indexOf(field) === -1) {
            var fieldParts = field.split('.'),
                finalField = fieldParts.pop(),
                toDelete = $scope.command;

            fieldParts.forEach(function(part) {
              if (toDelete) {
                toDelete = toDelete[part];
              }
            });

            if (toDelete) {
              delete toDelete[finalField];
            }
          }
        });
      }
      if (newStrategy && newStrategy.additionalFieldsTemplateUrl) {
        $scope.additionalFieldsTemplateUrl = newStrategy.additionalFieldsTemplateUrl;
      } else {
        $scope.additionalFieldsTemplateUrl = null;
      }
      if (newStrategy && newStrategy.initializationMethod) {
        newStrategy.initializationMethod($scope.command);
      }
    }

    $scope.deploymentStrategies = deploymentStrategyService.listAvailableStrategies($scope.command.selectedProvider);

    $scope.$watch('command.strategy', selectStrategy);

  });
