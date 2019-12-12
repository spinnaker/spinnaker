'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_ENABLEASG_KUBERNETESENABLEASGSTAGE =
  'spinnaker.kubernetes.pipeline.stage.enableAsgStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_ENABLEASG_KUBERNETESENABLEASGSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_ENABLEASG_KUBERNETESENABLEASGSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('./enableAsgExecutionDetails.html'),
      executionConfigSections: ['enableServerGroupConfig', 'taskStatus'],
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesEnableAsgStageCtrl', [
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

      $scope.targets = StageConstants.TARGET_LIST;

      stage.namespaces = stage.namespaces || [];
      stage.cloudProvider = 'kubernetes';
      stage.interestingHealthProviderNames = ['KubernetesService'];

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      if (!stage.target) {
        stage.target = $scope.targets[0].val;
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
