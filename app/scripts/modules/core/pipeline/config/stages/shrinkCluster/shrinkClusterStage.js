'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?
module.exports = angular.module('spinnaker.core.pipeline.stage.shrinkClusterStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'shrinkCluster',
      label: 'Shrink Cluster',
      description: 'Shrinks a cluster',
    });
  })
  .name;

