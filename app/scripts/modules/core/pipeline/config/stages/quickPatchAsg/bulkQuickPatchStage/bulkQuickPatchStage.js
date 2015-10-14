'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.quickPatchAsg.bulkQuickPatchStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'bulkQuickPatch',
      label: 'Bulk Quick Patch ASG',
      description: 'Bulk Quick Patches an ASG',
      executionDetailsUrl: require('./bulkQuickPatchStageExecutionDetails.html'),
    });
  }).name;
