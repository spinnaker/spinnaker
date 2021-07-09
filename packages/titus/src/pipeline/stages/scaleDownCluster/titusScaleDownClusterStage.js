'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const TITUS_PIPELINE_STAGES_SCALEDOWNCLUSTER_TITUSSCALEDOWNCLUSTERSTAGE =
  'spinnaker.titus.pipeline.stage.scaleDownClusterStage';
export const name = TITUS_PIPELINE_STAGES_SCALEDOWNCLUSTER_TITUSSCALEDOWNCLUSTERSTAGE; // for backwards compatibility
module(TITUS_PIPELINE_STAGES_SCALEDOWNCLUSTER_TITUSSCALEDOWNCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'scaleDownCluster',
      cloudProvider: 'titus',
      templateUrl: require('./scaleDownClusterStage.html'),
      executionConfigSections: ['scaleDownClusterConfig', 'taskStatus'],
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
  .controller('titusScaleDownClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('titus').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'titus';

      if (!stage.credentials && $scope.application.defaultCredentials.titus) {
        stage.credentials = $scope.application.defaultCredentials.titus;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.titus) {
        stage.regions.push($scope.application.defaultRegions.titus);
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
