'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.highlander', [])
  .config(function(deploymentStrategyConfigProvider) {
    deploymentStrategyConfigProvider.registerStrategy({
      label: 'Highlander',
      description: 'Destroys <i>all</i> previous server groups in the cluster as soon as new server group passes health checks',
      key: 'highlander',
    });
  });
