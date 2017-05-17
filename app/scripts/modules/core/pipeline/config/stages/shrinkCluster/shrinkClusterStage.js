'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.shrinkClusterStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'shrinkCluster',
      label: 'Shrink Cluster',
      description: 'Shrinks a cluster',
      strategy: true
    });
  });

