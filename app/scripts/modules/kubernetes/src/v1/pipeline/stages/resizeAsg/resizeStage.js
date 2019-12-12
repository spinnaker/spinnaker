'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

import './resizeStage.less';

export const KUBERNETES_V1_PIPELINE_STAGES_RESIZEASG_RESIZESTAGE = 'spinnaker.kubernetes.pipeline.stage.resizeStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_RESIZEASG_RESIZESTAGE; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_RESIZEASG_RESIZESTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'resizeServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./resizeStage.html'),
      executionDetailsUrl: require('./resizeExecutionDetails.html'),
      executionStepLabelUrl: require('./resizeStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'action' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('kubernetesResizeStageController', [
    '$scope',
    function($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.viewState = {
        accountsLoaded: false,
        namespacesLoaded: false,
      };

      AccountService.listAccounts('kubernetes').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.viewState.accountsLoaded = true;
      });

      $scope.resizeTargets = StageConstants.TARGET_LIST;

      $scope.scaleActions = [
        {
          label: 'Scale Up',
          val: 'scale_up',
        },
        {
          label: 'Scale Down',
          val: 'scale_down',
        },
        {
          label: 'Scale to Cluster Size',
          val: 'scale_to_cluster',
        },
        {
          label: 'Scale to Exact Size',
          val: 'scale_exact',
        },
      ];

      $scope.resizeTypes = [
        {
          label: 'Percentage',
          val: 'pct',
        },
        {
          label: 'Incremental',
          val: 'incr',
        },
      ];

      stage.capacity = stage.capacity || {};
      stage.namespaces = stage.namespaces || [];
      stage.target = stage.target || $scope.resizeTargets[0].val;
      stage.action = stage.action || $scope.scaleActions[0].val;
      stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;
      if (stage.resizeType === 'exact') {
        stage.action = 'scale_exact';
      }
      stage.cloudProvider = 'kubernetes';
      stage.cloudProviderType = 'kubernetes';

      if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
        stage.interestingHealthProviderNames = ['KubernetesPod'];
      }

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      ctrl.updateResizeType = function() {
        if (stage.action === 'scale_exact') {
          stage.resizeType = 'exact';
          delete stage.scalePct;
          delete stage.scaleNum;
        } else {
          stage.capacity = {};
          if (stage.resizeType === 'pct') {
            delete stage.scaleNum;
          } else if (stage.resizeType === 'incr') {
            delete stage.scalePct;
          }
        }
      };
    },
  ]);
