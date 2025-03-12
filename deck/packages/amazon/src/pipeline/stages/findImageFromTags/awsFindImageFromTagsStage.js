'use strict';

import { module } from 'angular';

import { BakeryReader, Registry } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_FINDIMAGEFROMTAGS_AWSFINDIMAGEFROMTAGSSTAGE =
  'spinnaker.amazon.pipeline.stage.findImageFromTagsStage';
export const name = AMAZON_PIPELINE_STAGES_FINDIMAGEFROMTAGS_AWSFINDIMAGEFROMTAGSSTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_FINDIMAGEFROMTAGS_AWSFINDIMAGEFROMTAGSSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'aws',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'packageName' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'tags' },
      ],
    });
  })
  .controller('awsFindImageFromTagsStageCtrl', [
    '$scope',
    function ($scope) {
      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.regions = $scope.stage.regions || [];
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';

      BakeryReader.getRegions('aws').then(function (regions) {
        $scope.regions = regions;
      });
    },
  ]);
