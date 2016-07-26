'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.destroyAsgStage', [
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
  require('./destroyAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('./destroyAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'namespaces', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('kubernetesDestroyAsgStageCtrl', function($scope, accountService, stageConstants) {

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

  });

