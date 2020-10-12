'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const ORACLE_PIPELINE_STAGES_DISABLEASG_DISABLEASGSTAGE = 'spinnaker.oracle.pipeline.stage.disableAsgStage';
export const name = ORACLE_PIPELINE_STAGES_DISABLEASG_DISABLEASGSTAGE; // for backwards compatibility
module(ORACLE_PIPELINE_STAGES_DISABLEASG_DISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'oracle',
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
  .controller('oracleDisableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      const provider = 'oracle';

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts(provider).then((accounts) => {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = provider;

      if (!stage.credentials && $scope.application.defaultCredentials.oracle) {
        stage.credentials = $scope.application.defaultCredentials.oracle;
      }

      if (!stage.regions.length && $scope.application.defaultRegions.gce) {
        stage.regions.push($scope.application.defaultRegions.oracle);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
