'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.none', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'None',
      description: 'Creates the next server group with no impact on existing server groups',
      key: '',
    });
  });
