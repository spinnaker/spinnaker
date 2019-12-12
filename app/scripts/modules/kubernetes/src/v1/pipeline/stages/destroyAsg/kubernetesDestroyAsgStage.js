'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_DESTROYASG_KUBERNETESDESTROYASGSTAGE =
  'spinnaker.kubernetes.pipeline.stage.destroyAsgStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_DESTROYASG_KUBERNETESDESTROYASGSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_DESTROYASG_KUBERNETESDESTROYASGSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('./destroyAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesDestroyAsgStageCtrl', [
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
    },
  ]);
