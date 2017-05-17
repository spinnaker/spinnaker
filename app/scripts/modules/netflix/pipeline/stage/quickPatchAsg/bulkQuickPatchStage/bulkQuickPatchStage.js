'use strict';

const angular = require('angular');

import { PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsg.bulkQuickPatchStage', [
  PIPELINE_CONFIG_PROVIDER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'bulkQuickPatch',
      label: 'Bulk Quick Patch ASG',
      description: 'Bulk Quick Patches an ASG',
      executionDetailsUrl: require('./bulkQuickPatchStageExecutionDetails.html'),
      executionConfigSection: ['bulkQuickPatchStageConfig', 'taskStatus'],
    });
  });
