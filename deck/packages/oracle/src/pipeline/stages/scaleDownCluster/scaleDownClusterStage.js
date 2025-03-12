'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const ORACLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE =
  'spinnaker.oracle.pipeline.stage.scaleDownClusterStage';
export const name = ORACLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE; // for backwards compatibility
module(ORACLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'oracle',
      templateUrl: require('./scaleDownClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        {
          type: 'requiredField',
          fieldName: 'remainingFullSizeServerGroups',
          fieldLabel: 'Keep [X] full size Server Groups',
        },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
      strategy: true,
    });
  })
  .controller('oracleScaleDownClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      const provider = 'oracle';

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts(provider).then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = provider;

      if (!stage.credentials && $scope.application.defaultCredentials.gce) {
        stage.credentials = $scope.application.defaultCredentials.gce;
      }

      if (!stage.regions.length && $scope.application.defaultRegions.gce) {
        stage.regions.push($scope.application.defaultRegions.gce);
      }

      if (stage.remainingFullSizeServerGroups === undefined) {
        stage.remainingFullSizeServerGroups = 1;
      }

      if (stage.allowScaleDownActive === undefined) {
        stage.allowScaleDownActive = false;
      }

      if (stage.preferLargerOverNewer === undefined) {
        stage.preferLargerOverNewer = 'false';
      }

      this.pluralize = function (str, val) {
        return val === 1 ? str : str + 's';
      };

      stage.preferLargerOverNewer = stage.preferLargerOverNewer.toString();
    },
  ]);
