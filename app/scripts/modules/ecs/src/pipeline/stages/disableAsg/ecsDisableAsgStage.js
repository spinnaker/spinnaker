'use strict';

import { module } from 'angular';

import { AccountService, PipelineTemplates, Registry, StageConstants } from '@spinnaker/core';

export const ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE = 'spinnaker.ecs.pipeline.stage.disableAsgStage';
export const name = ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE; // for backwards compatibility
module(ECS_PIPELINE_STAGES_DISABLEASG_ECSDISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'ecs',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: PipelineTemplates.disableAsgExecutionDetails,
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('ecsDisableAsgStageCtrl', [
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

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'ecs';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Ecs'];
      }

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
