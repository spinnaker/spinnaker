'use strict';

import { module } from 'angular';

import { PipelineConfigService, Registry, StageConstants } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_TAGIMAGE_AWSTAGIMAGESTAGE = 'spinnaker.amazon.pipeline.stage.tagImageStage';
export const name = AMAZON_PIPELINE_STAGES_TAGIMAGE_AWSTAGIMAGESTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_TAGIMAGE_AWSTAGIMAGESTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'upsertImageTags',
      cloudProvider: 'aws',
      templateUrl: require('./tagImageStage.html'),
      executionDetailsUrl: require('./tagImageExecutionDetails.html'),
      executionConfigSections: ['tagImageConfig', 'taskStatus'],
    });
  })
  .controller('awsTagImageStageCtrl', [
    '$scope',
    ($scope) => {
      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'aws';

      const initUpstreamStages = () => {
        const upstreamDependencies = PipelineConfigService.getAllUpstreamDependencies(
          $scope.pipeline,
          $scope.stage,
        ).filter((stage) => StageConstants.IMAGE_PRODUCING_STAGES.includes(stage.type));
        $scope.consideredStages = new Map(upstreamDependencies.map((stage) => [stage.refId, stage.name]));
      };
      $scope.$watch('stage.requisiteStageRefIds', initUpstreamStages);
    },
  ]);
