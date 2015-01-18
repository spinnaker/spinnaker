'use strict';


angular.module('deckApp.deploymentStrategy')
  .factory('deploymentStrategyService', function (deploymentStrategyConfig, _) {

    function listAvailableStrategies() {
      return deploymentStrategyConfig.listStrategies();
    }

    function getStrategy(key) {
      return  _.find(deploymentStrategyConfig.listStrategies(), { key: key });
    }

    return {
      listAvailableStrategies: listAvailableStrategies,
      getStrategy: getStrategy
    };

  });
