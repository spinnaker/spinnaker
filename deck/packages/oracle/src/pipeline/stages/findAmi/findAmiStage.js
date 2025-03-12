'use strict';

import * as angular from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const ORACLE_PIPELINE_STAGES_FINDAMI_FINDAMISTAGE = 'spinnaker.oracle.pipeline.stage.findAmiStage';
export const name = ORACLE_PIPELINE_STAGES_FINDAMI_FINDAMISTAGE; // for backwards compatibility
angular
  .module(ORACLE_PIPELINE_STAGES_FINDAMI_FINDAMISTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImage',
      cloudProvider: 'oracle',
      templateUrl: require('./findAmiStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials' },
      ],
    });
  })
  .controller('oracleFindAmiStageCtrl', [
    '$scope',
    ($scope) => {
      const provider = 'oracle';

      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts(provider).then((accounts) => {
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
      stage.cloudProvider = provider;
      stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

      if (angular.isUndefined(stage.onlyEnabled)) {
        stage.onlyEnabled = true;
      }

      if (!stage.credentials && $scope.application.defaultCredentials.oracle) {
        stage.credentials = $scope.application.defaultCredentials.oracle;
      }

      if (!stage.regions.length && $scope.application.defaultRegions.oracle) {
        stage.regions.push($scope.application.defaultRegions.oracle);
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
