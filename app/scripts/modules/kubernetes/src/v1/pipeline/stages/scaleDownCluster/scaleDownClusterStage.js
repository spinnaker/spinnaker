'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE =
  'spinnaker.kubernetes.pipeline.stage.scaleDownClusterStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'kubernetes',
      templateUrl: require('./scaleDownClusterStage.html'),
      executionDetailsUrl: require('./scaleDownClusterExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        {
          type: 'requiredField',
          fieldName: 'remainingFullSizeServerGroups',
          fieldLabel: 'Keep [X] full size Server Groups',
        },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
      strategy: true,
    });
  })
  .controller('kubernetesScaleDownClusterStageCtrl', [
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

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      if (stage.remainingFullSizeServerGroups === undefined) {
        stage.remainingFullSizeServerGroups = 1;
      }

      if (stage.allowScaleDownActive === undefined) {
        stage.allowScaleDownActive = false;
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
