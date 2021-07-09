'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const TITUS_PIPELINE_STAGES_DISABLEASG_TITUSDISABLEASGSTAGE = 'spinnaker.titus.pipeline.stage.disableAsgStage';
export const name = TITUS_PIPELINE_STAGES_DISABLEASG_TITUSDISABLEASGSTAGE; // for backwards compatibility
module(TITUS_PIPELINE_STAGES_DISABLEASG_TITUSDISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'titus',
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
  .controller('titusDisableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('titus').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'titus';

      if (
        stage.isNew &&
        $scope.application.attributes.platformHealthOnlyShowOverride &&
        $scope.application.attributes.platformHealthOnly
      ) {
        stage.interestingHealthProviderNames = ['Titus'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.titus) {
        stage.credentials = $scope.application.defaultCredentials.titus;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.titus) {
        stage.regions.push($scope.application.defaultRegions.titus);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
