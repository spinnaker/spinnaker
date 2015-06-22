'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.quickPatchAsg.bulkQuickPatchStage')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'bulkQuickPatch',
      label: 'Bulk Quick Patch ASG',
      description: 'Bulk Quick Patches an ASG',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/quickPatchAsg/bulkQuickPatchStage/bulkQuickPatchStageExecutionDetails.html'
    });
  });
