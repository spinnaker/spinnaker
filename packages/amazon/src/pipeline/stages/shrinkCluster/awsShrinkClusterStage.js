'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const AMAZON_PIPELINE_STAGES_SHRINKCLUSTER_AWSSHRINKCLUSTERSTAGE =
  'spinnaker.amazon.pipeline.stage.aws.shrinkClusterStage';
export const name = AMAZON_PIPELINE_STAGES_SHRINKCLUSTER_AWSSHRINKCLUSTERSTAGE; // for backwards compatibility
module(AMAZON_PIPELINE_STAGES_SHRINKCLUSTER_AWSSHRINKCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'aws',
      templateUrl: require('./shrinkClusterStage.html'),
      accountExtractor: (stage) => [stage.context.credentials],
      configAccountExtractor: (stage) => [stage.credentials],
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('awsShrinkClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'aws';

      if (!stage.credentials && $scope.application.defaultCredentials.aws) {
        stage.credentials = $scope.application.defaultCredentials.aws;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.aws) {
        stage.regions.push($scope.application.defaultRegions.aws);
      }

      if (stage.shrinkToSize === undefined) {
        stage.shrinkToSize = 1;
      }

      if (stage.allowDeleteActive === undefined) {
        stage.allowDeleteActive = false;
      }

      ctrl.pluralize = function (str, val) {
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
