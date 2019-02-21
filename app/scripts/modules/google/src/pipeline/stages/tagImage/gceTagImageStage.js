'use strict';

const angular = require('angular');

import { AccountService, Registry, PipelineConfigService, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.gce.pipeline.stage..tagImageStage', [])
  .config(function() {
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
    $scope => {
      AccountService.listAccounts('gce').then(accounts => ($scope.accounts = accounts));

      $scope.stage.tags = $scope.stage.tags || {};
      $scope.stage.cloudProvider = $scope.stage.cloudProvider || 'gce';

      const initUpstreamStages = () => {
        const upstreamDependencies = PipelineConfigService.getAllUpstreamDependencies(
          $scope.pipeline,
          $scope.stage,
        ).filter(stage => StageConstants.IMAGE_PRODUCING_STAGES.includes(stage.type));
        $scope.consideredStages = new Map(upstreamDependencies.map(stage => [stage.refId, stage.name]));
      };
      $scope.$watch('pipeline.stages', initUpstreamStages);
    },
  ]);
