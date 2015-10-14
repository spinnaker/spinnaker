'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.strategy.provider', [])
  .provider('fastPropertyStrategy', function () {

    var strategies = [];

    this.registerStrategy = function(strategy) {
      strategies.push(strategy);
    };

    var isEnabled = (strategy) => {
      return strategy.enabled;
    };

    this.$get = function() {
      return {
        getStrategies: function() {
          return angular.copy(strategies.filter(isEnabled));
        }
      };
    };
  })
  .name;
