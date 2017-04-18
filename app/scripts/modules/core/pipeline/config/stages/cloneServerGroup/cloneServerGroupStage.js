'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cloneServerGroupStage', [
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'cloneServerGroup',
      label: 'Clone Server Group',
      description: 'Clones a server group',
      strategy: false,
    });
  });

