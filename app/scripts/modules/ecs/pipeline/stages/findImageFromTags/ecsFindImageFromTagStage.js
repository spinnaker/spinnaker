'use strict';

const angular = require('angular');

import { PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.pipeline.stage.findImageFromTagsStage', [PIPELINE_CONFIG_PROVIDER])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'ecs',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
    });
  })
  .controller('ecsFindImageFromTagsStageCtrl', function($scope) {
    $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'ecs';
  });
