'use strict';

let angular = require('angular');
let scopedLadderStrategyTemplate = require('./scopeLadder.html');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.scopeLadder.config', [
    require('../../fastProperty.strategy.provider.js')
  ])
  .config((fastPropertyStrategyProvider) => {

    fastPropertyStrategyProvider.registerStrategy({
      key: 'manualScopedPropagation',
      wizardScreens: ['Target', 'Review'],
      label: 'Manual Scope Propagation',
      description: 'Select the base scope and propagate the fast property up to the global scope',
      templateUrl: scopedLadderStrategyTemplate,
      controller: 'ScopeLadderStrategyController',
      controllerAs: 'strategy',
      enabled: true,
    });

  });
