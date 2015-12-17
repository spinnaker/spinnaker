'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scaleDownClusterStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'scaleDownCluster',
      label: 'Scale Down Cluster',
      description: 'Scales down a cluster',
      strategy: true,
    });
  });

