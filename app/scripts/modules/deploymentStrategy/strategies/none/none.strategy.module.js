'use strict';

angular.module('spinnaker.deploymentStrategy.none', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'None',
      description: 'Creates the next server group with no impact on existing server groups',
      key: '',
    });
  });
