'use strict';

import { module } from 'angular';

import { AccountService, PipelineConfigService, Registry, StageConstants } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE = 'spinnaker.gce.pipeline.stage..tagImageStage';
export const name = GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'gce',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
      executionConfigSections: ['tagImageConfig', 'taskStatus'],
    });
  })
  .controller('gceTagImageStageCtrl', [
    '$scope',
    ($scope) => {
      AccountService.listAccounts('gce').then((accounts) => ($scope.accounts = accounts));

      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'gce';

      const initUpstreamStages = () => {
        const upstreamDependencies = PipelineConfigService.getAllUpstreamDependencies(
          $scope.pipeline,
          $scope.stage,
        ).filter((stage) => StageConstants.IMAGE_PRODUCING_STAGES.includes(stage.type));
        $scope.consideredStages = new Map(upstreamDependencies.map((stage) => [stage.refId, stage.name]));
      };
      $scope.$watch('pipeline.stages', initUpstreamStages);
    },
  ]);
