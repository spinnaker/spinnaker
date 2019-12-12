'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_DISABLECLUSTER_KUBERNETESDISABLECLUSTERSTAGE =
  'spinnaker.kubernetes.pipeline.stage.disableClusterStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_DISABLECLUSTER_KUBERNETESDISABLECLUSTERSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_DISABLECLUSTER_KUBERNETESDISABLECLUSTERSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'disableCluster',
      cloudProvider: 'kubernetes',
      templateUrl: require('./disableClusterStage.html'),
      executionDetailsUrl: require('./disableClusterExecutionDetails.html'),
      executionConfigSections: ['disableClusterConfig', 'taskStatus'],
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        {
          type: 'requiredField',
          fieldName: 'remainingEnabledServerGroups',
          fieldLabel: 'Keep [X] enabled Server Groups',
        },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesDisableClusterStageCtrl', [
    '$scope',
    function($scope) {
      const ctrl = this;

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
      stage.cloudProvider = 'kubernetes';
      stage.interestingHealthProviderNames = ['KubernetesService'];

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      if (stage.remainingEnabledServerGroups === undefined) {
        stage.remainingEnabledServerGroups = 1;
      }

      ctrl.pluralize = function(str, val) {
        if (val === 1) {
          return str;
        }
        return str + 's';
      };

      if (stage.preferLargerOverNewer === undefined) {
        stage.preferLargerOverNewer = 'false';
      }
      stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
    },
  ]);
