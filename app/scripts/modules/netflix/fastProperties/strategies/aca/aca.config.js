'use strict';

let angular = require('angular');
let acaSrategyTemplate = require('./aca.html');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.aca.config', [
    require('../../fastProperty.strategy.provider.js')
  ])
  .config((fastPropertyStrategyProvider) => {
    fastPropertyStrategyProvider.registerStrategy({
      key: 'aca',
      label: 'ACA Propagation',
      description: 'Rollout Fast Property to a percentage of instances and start an ACA',
      templateUrl: acaSrategyTemplate,
      controller: 'acaStrategyController',
      controllerAs: 'strategy',
      enabled: false,
    });

  })
  .name;
