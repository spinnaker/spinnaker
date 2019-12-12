'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE =
  'spinnaker.kubernetes.pipeline.stage.shrinkClusterStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'kubernetes',
      templateUrl: require('./shrinkClusterStage.html'),
      executionDetailsUrl: require('./shrinkClusterExecutionDetails.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesShrinkClusterStageCtrl', [
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

      if (stage.shrinkToSize === undefined) {
        stage.shrinkToSize = 1;
      }

      if (stage.allowDeleteActive === undefined) {
        stage.allowDeleteActive = false;
      }

      ctrl.pluralize = function(str, val) {
        if (val === 1) {
          return str;
        }
        return str + 's';
      };

      if (stage.retainLargerOverNewer === undefined) {
        stage.retainLargerOverNewer = 'false';
      }
      stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
    },
  ]);
