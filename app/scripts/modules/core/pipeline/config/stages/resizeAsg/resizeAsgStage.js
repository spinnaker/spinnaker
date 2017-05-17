'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.resizeAsgStage', [
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'resizeServerGroup',
      label: 'Resize Server Group',
      description: 'Resizes a server group',
      strategy: true,
    });
  });

