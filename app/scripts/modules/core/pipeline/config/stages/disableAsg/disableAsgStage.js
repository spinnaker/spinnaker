'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableAsgStage', [
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'disableServerGroup',
      label: 'Disable Server Group',
      description: 'Disables a server group',
      strategy: true,
    });
  });
