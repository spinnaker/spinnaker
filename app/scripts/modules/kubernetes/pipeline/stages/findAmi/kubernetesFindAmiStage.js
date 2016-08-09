'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.findAmiStage', [
  require('../../../../core/application/listExtractor/listExtractor.service.js'),
  require('./findAmiExecutionDetails.controller.js'),
  require('../../../../core/account/account.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'findImage',
      cloudProvider: 'kubernetes',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection'},
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'imageNamePattern' },
        { type: 'requiredField', fieldName: 'credentials' }
      ]
    });
  }).controller('kubernetesFindAmiStageController', function($scope, accountService) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('kubernetes').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.selectionStrategies = [{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple server groups exist, prefer the server group with the most instances'
    }, {
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple server groups exist, prefer the newest'
    }, {
      label: 'Oldest',
      val: 'OLDEST',
      description: 'When multiple server groups exist, prefer the oldest'
    }, {
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple server groups exist, fail'
    }];

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
  });

