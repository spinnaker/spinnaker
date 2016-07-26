'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.enableAsgStage', [
  require('../../../../core/application/listExtractor/listExtractor.service.js'),
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
  require('./enableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('./enableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'namespaces' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ]
    });
  }).controller('kubernetesEnableAsgStageCtrl', function($scope, accountService, stageConstants) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      namespacesLoaded: false
    };

    accountService.listAccounts('kubernetes').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.targets = stageConstants.targetList;

    stage.namespaces = stage.namespaces || [];
    stage.cloudProvider = 'kubernetes';
    stage.interestingHealthProviderNames = ['KubernetesService'];

    if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      stage.credentials = $scope.application.defaultCredentials.kubernetes;
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

