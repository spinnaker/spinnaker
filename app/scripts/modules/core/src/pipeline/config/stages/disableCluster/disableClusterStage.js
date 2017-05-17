'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableClusterStage', [
  PIPELINE_CONFIG_PROVIDER
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

