'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.strategy.provider', [])
  .provider('fastPropertyStrategy', function () {

    var strategies = [];

    this.registerStrategy = function({
        key = 'aca',
        wizardScreens = ['Review'],
        label = 'ACA Propagation',
        description = 'Rollout Fast Property to a percentage of instances and start an ACA',
        templateUrl = 'unknown',
        controller = 'unknown',
        controllerAs = 'unkown',
        enabled = false,
        display = (screen) => this.wizardScreens.indexOf(screen) > -1
      } = {} ) {
      strategies.push({
        key: key,
        wizardScreens: wizardScreens,
        label: label,
        description: description,
        templateUrl: templateUrl,
        controller: controller,
        controllerAs:controllerAs,
        enabled:enabled,
        display: display
      });
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
  });
