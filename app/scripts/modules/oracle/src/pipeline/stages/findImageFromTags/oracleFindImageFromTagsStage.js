'use strict';

import { module } from 'angular';

import { BakeryReader, Registry } from '@spinnaker/core';

export const ORACLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ORACLEFINDIMAGEFROMTAGSSTAGE =
  'spinnaker.oracle.pipeline.stage.findImageFromTagsStage';
export const name = ORACLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ORACLEFINDIMAGEFROMTAGSSTAGE; // for backwards compatibility
module(ORACLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ORACLEFINDIMAGEFROMTAGSSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImageFromTags',
      cloudProvider: 'oracle',
      templateUrl: require('./findImageFromTagsStage.html'),
      executionDetailsUrl: require('./findImageFromTagsExecutionDetails.html'),
      executionConfigSections: ['findImageConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'packageName' },
      ],
    });
  })
  .controller('oracleFindImageFromTagsStageCtrl', [
    '$scope',
    function ($scope) {
      $scope.stage.packageName = $scope.stage.packageName || '*';
      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.regions = $scope.stage.regions || [];
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'oracle';

      BakeryReader.getRegions('oracle').then(function (regions) {
        $scope.regions = regions;
      });
    },
  ]);
