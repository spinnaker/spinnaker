'use strict';

import { module } from 'angular';

import { Registry } from '@spinnaker/core';

export const ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE =
  'spinnaker.ecs.pipeline.stage.findImageFromTagsStage';
export const name = ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE; // for backwards compatibility
module(ECS_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ECSFINDIMAGEFROMTAGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'ecs',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [{ type: 'requiredField', fieldName: 'imageLabelOrSha' }],
    });
  })
  .controller('ecsFindImageFromTagsStageCtrl', [
    '$scope',
    function ($scope) {
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'ecs';
    },
  ]);
