'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const DCOS_PIPELINE_STAGES_DISABLEASG_DCOSDISABLEASGSTAGE = 'spinnaker.dcos.pipeline.stage.disableAsgStage';
export const name = DCOS_PIPELINE_STAGES_DISABLEASG_DCOSDISABLEASGSTAGE; // for backwards compatibility
module(DCOS_PIPELINE_STAGES_DISABLEASG_DCOSDISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'dcos',
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
  .controller('dcosDisableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('dcos').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'dcos';

      if (!stage.credentials && $scope.application.defaultCredentials.dcos) {
        stage.credentials = $scope.application.defaultCredentials.dcos;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.dcos) {
        stage.regions.push($scope.application.defaultRegions.dcos);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
