'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE =
  'spinnaker.gce.pipeline.stage..scaleDownClusterStage';
export const name = GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'gce',
      templateUrl: require('./scaleDownClusterStage.html'),
      accountExtractor: (stage) => [stage.context.credentials],
      configAccountExtractor: (stage) => [stage.credentials],
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
  .controller('gceScaleDownClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'gce';

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

      ctrl.pluralize = function (str, val) {
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
