'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const AZURE_PIPELINE_STAGES_DISABLEASG_AZUREDISABLEASGSTAGE = 'spinnaker.azure.pipeline.stage.disableAsgStage';
export const name = AZURE_PIPELINE_STAGES_DISABLEASG_AZUREDISABLEASGSTAGE; // for backwards compatibility
module(AZURE_PIPELINE_STAGES_DISABLEASG_AZUREDISABLEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'azure',
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
  .controller('azureDisableAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('azure').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.targets = StageConstants.TARGET_LIST;

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'azure';

      if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
        stage.interestingHealthProviderNames = []; // bypass the check for now; will change this later to ['azureService']
      }

      if (!stage.credentials && $scope.application.defaultCredentials.azure) {
        stage.credentials = $scope.application.defaultCredentials.azure;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.azure) {
        stage.regions.push($scope.application.defaultRegions.azure);
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
