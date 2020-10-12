'use strict';

import { module } from 'angular';

import { BakeryReader, Registry } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE =
  'spinnaker.gce.pipeline.stage..findImageFromTagsStage';
export const name = GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'gce',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'packageName' },
        { type: 'requiredField', fieldName: 'tags' },
      ],
    });
  })
  .controller('gceFindImageFromTagsStageCtrl', [
    '$scope',
    function ($scope) {
      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.regions = $scope.stage.regions || [];
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'gce';

      BakeryReader.getRegions('gce').then(function (regions) {
        $scope.regions = regions;
      });
    },
  ]);
