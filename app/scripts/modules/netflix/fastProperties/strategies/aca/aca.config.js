'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.aca.config', [
    require('../../fastProperty.strategy.provider.js')
  ])
  .config((fastPropertyStrategyProvider) => {
    fastPropertyStrategyProvider.registerStrategy({
      key: 'aca',
      wizardScreens: ['Target', 'ACAConfig', 'ACAInstanceSelector', 'Review'],
      label: 'ACA Propagation',
      description: 'Rollout Fast Property to a percentage of instances and start an ACA',
      controller: 'acaStrategyController',
      controllerAs: 'strategy',
      enabled: true,
    });

  });
