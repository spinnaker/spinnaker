'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const TITUS_PIPELINE_STAGES_SHRINKCLUSTER_TITUSSHRINKCLUSTERSTAGE =
  'spinnaker.titus.pipeline.stage.shrinkClusterStage';
export const name = TITUS_PIPELINE_STAGES_SHRINKCLUSTER_TITUSSHRINKCLUSTERSTAGE; // for backwards compatibility
module(TITUS_PIPELINE_STAGES_SHRINKCLUSTER_TITUSSHRINKCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'titus',
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
  .controller('titusShrinkClusterStageCtrl', [
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
