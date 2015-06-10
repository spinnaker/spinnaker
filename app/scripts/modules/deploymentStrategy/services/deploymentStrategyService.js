'use strict';


angular.module('spinnaker.deploymentStrategy')
  .factory('deploymentStrategyService', function (deploymentStrategyConfig, _) {

    function listAvailableStrategies(provider) {
      var allStrategies = deploymentStrategyConfig.listStrategies();
      return allStrategies.filter(function (strategy) {
        return !strategy.providers || !strategy.providers.length || strategy.providers.indexOf(provider) !== -1;
      });
    }

    function getStrategy(key) {
      return  _.find(deploymentStrategyConfig.listStrategies(), { key: key });
    }

    return {
      listAvailableStrategies: listAvailableStrategies,
      getStrategy: getStrategy
    };

  });
