'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.core.pipeline.stage.kubernetes.disableAsgStage', [
  require('core/application/modal/platformHealthOverride.directive.js'),
  require('core/pipeline/config/stages/stageConstants.js'),
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'kubernetes',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('./disableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'namespaces', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('kubernetesDisableAsgStageController', function($scope, accountService, stageConstants) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      namespacesLoaded: false
    };

    accountService.listAccounts('kubernetes').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    stage.namespaces = stage.namespaces || [];

    $scope.targets = stageConstants.targetList;

    stage.cloudProvider = 'kubernetes';
    stage.interestingHealthProviderNames = ['KubernetesService'];

    if (!stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      stage.credentials = $scope.application.defaultCredentials.kubernetes;
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  });

