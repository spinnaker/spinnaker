'use strict';

import * as angular from 'angular';
import { AccountService, Registry } from '@spinnaker/core';

export const DCOS_PIPELINE_STAGES_FINDAMI_DCOSFINDAMISTAGE = 'spinnaker.dcos.pipeline.stage.findAmiStage';
export const name = DCOS_PIPELINE_STAGES_FINDAMI_DCOSFINDAMISTAGE; // for backwards compatibility
angular
  .module(DCOS_PIPELINE_STAGES_FINDAMI_DCOSFINDAMISTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'findImage',
      cloudProvider: 'dcos',
      templateUrl: require('./findAmiStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
        { type: 'requiredField', fieldName: 'credentials' },
      ],
    });
  })
  .controller('dcosFindAmiStageController', [
    '$scope',
    function ($scope) {
      const stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('dcos').then(function (accounts) {
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

      stage.cloudProvider = 'dcos';
      stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

      if (angular.isUndefined(stage.onlyEnabled)) {
        stage.onlyEnabled = true;
      }

      if (!stage.credentials && $scope.application.defaultCredentials.dcos) {
        stage.credentials = $scope.application.defaultCredentials.dcos;
      }

      $scope.$watch('stage.credentials', $scope.accountUpdated);
    },
  ]);
