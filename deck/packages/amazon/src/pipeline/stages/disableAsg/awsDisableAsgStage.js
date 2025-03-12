'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_DISABLEASG_AWSDISABLEASGSTAGE = 'spinnaker.amazon.pipeline.stage.disableAsgStage';
export const name = AMAZON_PIPELINE_STAGES_DISABLEASG_AWSDISABLEASGSTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_DISABLEASG_AWSDISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'aws',
      templateUrl: require('./disableAsgStage.html'),
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
  .controller('awsDisableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'aws';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Amazon'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.aws) {
        stage.credentials = $scope.application.defaultCredentials.aws;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.aws) {
        stage.regions.push($scope.application.defaultRegions.aws);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
