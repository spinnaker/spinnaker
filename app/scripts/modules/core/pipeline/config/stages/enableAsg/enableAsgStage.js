'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.enableAsgStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'enableServerGroup',
      label: 'Enable Server Group',
      description: 'Enables a server group',
      strategy: true,
    });
  });
