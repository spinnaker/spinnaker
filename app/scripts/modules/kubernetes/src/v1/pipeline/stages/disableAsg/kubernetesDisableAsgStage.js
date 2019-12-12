'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_DISABLEASG_KUBERNETESDISABLEASGSTAGE =
  'spinnaker.kubernetes.pipeline.stage.disableAsgStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_DISABLEASG_KUBERNETESDISABLEASGSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_DISABLEASG_KUBERNETESDISABLEASGSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('./disableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesDisableAsgStageController', [
    '$scope',
    function($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        namespacesLoaded: false,
      };

      AccountService.listAccounts('kubernetes').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.namespaces = stage.namespaces || [];

      $scope.targets = StageConstants.TARGET_LIST;

      stage.cloudProvider = 'kubernetes';
      stage.interestingHealthProviderNames = ['KubernetesService'];

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }
    },
  ]);
