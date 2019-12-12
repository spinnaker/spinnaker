'use strict';

import * as angular from 'angular';

import { AccountService, Registry } from '@spinnaker/core';

export const KUBERNETES_V1_PIPELINE_STAGES_FINDAMI_KUBERNETESFINDAMISTAGE =
  'spinnaker.kubernetes.pipeline.stage.findAmiStage';
export const name = KUBERNETES_V1_PIPELINE_STAGES_FINDAMI_KUBERNETESFINDAMISTAGE; // for backwards compatibility
angular
  .module(KUBERNETES_V1_PIPELINE_STAGES_FINDAMI_KUBERNETESFINDAMISTAGE, [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'findImage',
      cloudProvider: 'kubernetes',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials' },
      ],
    });
  })
  .controller('kubernetesFindAmiStageController', [
    '$scope',
    function($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('kubernetes').then(function(accounts) {
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

      stage.namespaces = stage.namespaces || [];
      stage.cloudProvider = 'kubernetes';
      stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

      if (angular.isUndefined(stage.onlyEnabled)) {
        stage.onlyEnabled = true;
      }

      if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
        stage.credentials = $scope.application.defaultCredentials.kubernetes;
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
