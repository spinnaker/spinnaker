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
    // Use undefined to check for the presence of the 'strategy' field, which is added to the command
    // on "clone" operations, but not "create new" operations, where it doesn't seem valid to have a strategy
    // (assuming "create new" is used to create a brand new cluster).
    //
    // The field is hidden on the form if no deployment strategies are present on the scope.
    if ($scope.command.strategy !== undefined) {
      $scope.deploymentStrategies = deploymentStrategyService.listAvailableStrategies();
    }

    $scope.$watch('command.strategy', selectStrategy);

  });
