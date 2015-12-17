'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableClusterStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'disableCluster',
      label: 'Disable Cluster',
      description: 'Disables a cluster',
      strategy: true,
    });
  });

