'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJobStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'runJob',
      label: 'Run Job',
      description: 'Runs a container'
    });
  });

