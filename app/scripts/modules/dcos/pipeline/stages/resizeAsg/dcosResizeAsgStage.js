'use strict';

import { module } from 'angular';

import { AccountService, Registry, StageConstants } from '@spinnaker/core';

export const DCOS_PIPELINE_STAGES_RESIZEASG_DCOSRESIZEASGSTAGE = 'spinnaker.dcos.pipeline.stage.resizeAsgStage';
export const name = DCOS_PIPELINE_STAGES_RESIZEASG_DCOSRESIZEASGSTAGE; // for backwards compatibility
module(DCOS_PIPELINE_STAGES_RESIZEASG_DCOSRESIZEASGSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'resizeServerGroup',
      alias: 'resizeAsg',
      cloudProvider: 'dcos',
      templateUrl: require('./resizeAsgStage.html'),
      executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./resizeAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message:
            'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
        },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'action' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('dcosResizeAsgStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.viewState = {
        accountsLoaded: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('dcos').then(function (accounts) {
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
      stage.regions = stage.regions || [];
      stage.target = stage.target || $scope.resizeTargets[0].val;
      stage.action = stage.action || $scope.scaleActions[0].val;
      stage.resizeType = stage.resizeType || $scope.resizeTypes[0].val;
      if (!stage.action && stage.resizeType === 'exact') {
        stage.action = 'scale_exact';
      }
      stage.cloudProvider = 'dcos';

      if (!stage.credentials && $scope.application.defaultCredentials.dcos) {
        stage.credentials = $scope.application.defaultCredentials.dcos;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.dcos) {
        stage.regions.push($scope.application.defaultRegions.dcos);
      }

      ctrl.updateResizeType = function () {
        if (stage.action === 'scale_exact') {
          stage.resizeType = 'exact';
          delete stage.scalePct;
          delete stage.scaleNum;
        } else {
          stage.capacity = {};
          if (stage.resizeType === 'pct') {
            delete stage.scaleNum;
          } else {
            stage.resizeType = 'incr';
            delete stage.scalePct;
            stage.scaleNum = stage.scaleNum || 0;
          }
        }
      };
    },
  ]);
