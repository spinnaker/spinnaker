'use strict';

import * as angular from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const TITUS_PIPELINE_STAGES_FINDAMI_TITUSFINDAMISTAGE = 'spinnaker.titus.pipeline.stage.findAmiStage';
export const name = TITUS_PIPELINE_STAGES_FINDAMI_TITUSFINDAMISTAGE; // for backwards compatibility
angular
  .module(TITUS_PIPELINE_STAGES_FINDAMI_TITUSFINDAMISTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImage',
      alias: 'findAmi',
      cloudProvider: 'titus',
      templateUrl: require('./findAmiStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
        { type: 'requiredField', fieldName: 'credentials' },
      ],
    });
  })
  .controller('titusFindAmiStageCtrl', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('titus').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      $scope.selectionStrategies = [
        {
          label: 'Largest',
          val: 'LARGEST',
          description: 'When multiple server groups exist, prefer the server group with the most instances',
        },
        {
          label: 'Newest',
          val: 'NEWEST',
          description: 'When multiple server groups exist, prefer the newest',
        },
        {
          label: 'Oldest',
          val: 'OLDEST',
          description: 'When multiple server groups exist, prefer the oldest',
        },
        {
          label: 'Fail',
          val: 'FAIL',
          description: 'When multiple server groups exist, fail',
        },
      ];

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'titus';
      stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

      if (angular.isUndefined(stage.onlyEnabled)) {
        stage.onlyEnabled = true;
      }

      if (!stage.credentials && $scope.application.defaultCredentials.titus) {
        stage.credentials = $scope.application.defaultCredentials.titus;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.titus) {
        stage.regions.push($scope.application.defaultRegions.titus);
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
