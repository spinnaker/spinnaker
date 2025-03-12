'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE = 'spinnaker.ecs.pipeline.stage.ecs.destroyAsgStage';
export const name = ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE; // for backwards compatibility
module(ECS_PIPELINE_STAGES_DESTROYASG_ECSDESTROYASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      alias: 'destroyAsg',
      cloudProvider: 'ecs',
      templateUrl: require('./destroyAsgStage.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      accountExtractor: (stage) => [stage.context.credentials],
      configAccountExtractor: (stage) => [stage.credentials],
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('ecsDestroyAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('ecs').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'ecs';

      if (!stage.credentials && $scope.application.defaultCredentials.ecs) {
        stage.credentials = $scope.application.defaultCredentials.ecs;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.ecs) {
        stage.regions.push($scope.application.defaultRegions.ecs);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
