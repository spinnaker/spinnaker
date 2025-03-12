'use strict';

import { module } from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE =
  'spinnaker.ecs.pipeline.stage.ecs.shrinkClusterStage';
export const name = ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE; // for backwards compatibility
module(ECS_PIPELINE_STAGES_SHRINKCLUSTER_ECSSHRINKCLUSTERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'ecs',
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
  .controller('ecsShrinkClusterStageCtrl', [
    '$scope',
    function ($scope) {
      const ctrl = this;

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('ecs').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'ecs';

      if (!stage.credentials && $scope.application.defaultCredentials.ecs) {
        stage.credentials = $scope.application.defaultCredentials.ecs;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.ecs) {
        stage.regions.push($scope.application.defaultRegions.ecs);
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
