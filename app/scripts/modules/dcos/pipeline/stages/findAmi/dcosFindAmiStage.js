'use strict';

const angular = require('angular');
import { ACCOUNT_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.dcos.pipeline.stage.findAmiStage', [ACCOUNT_SERVICE])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
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
  .controller('dcosFindAmiStageController', function($scope, accountService) {
    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    accountService.listAccounts('dcos').then(function(accounts) {
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
  });
