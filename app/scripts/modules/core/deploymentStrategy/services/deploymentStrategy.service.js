'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.deploymentStrategy.service', [
  require('../deploymentStrategyConfig.provider.js')
])
  .factory('deploymentStrategyService', function (deploymentStrategyConfig) {

    function listAvailableStrategies(provider) {
      var allStrategies = deploymentStrategyConfig.listStrategies();
      return allStrategies.filter(function (strategy) {
        return !strategy.providers || !strategy.providers.length || strategy.providers.includes(provider);
      });
    }

    function getStrategy(key) {
      return _.find(deploymentStrategyConfig.listStrategies(), { key: key });
    }

    return {
      listAvailableStrategies: listAvailableStrategies,
      getStrategy: getStrategy
    };

  });
