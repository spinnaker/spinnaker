'use strict';

angular.module('deckApp.deploymentStrategy')
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
