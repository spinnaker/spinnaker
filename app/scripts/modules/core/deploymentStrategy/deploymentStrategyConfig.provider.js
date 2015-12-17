'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.deploymentStrategyConfigProvider', [
])
  .provider('deploymentStrategyConfig', function() {

    var strategies = [];

    function registerStrategy(strategy) {
      strategies.push(strategy);
    }

    function listStrategies() {
      return angular.copy(strategies);
    }

    this.registerStrategy = registerStrategy;

    this.$get = function() {
      return {
        listStrategies: listStrategies
      };
    };

  }
);
