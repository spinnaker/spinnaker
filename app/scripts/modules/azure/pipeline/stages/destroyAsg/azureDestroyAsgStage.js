'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.azure.destroyAsgStage', [
  require('../../../../core/account/account.service.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'azure',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('../../../../core/pipeline/config/stages/destroyAsg/templates/destroyAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('azureDestroyAsgStageCtrl', function($scope, accountService, stageConstants) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };


    accountService.listAccounts('azure').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      accountService.getAccountDetails(stage.credentials).then(function(details) {
        stage.regions = [details.org];
//        stage.regions = ['eastus', 'westus'];
      });
    };

    $scope.targets = stageConstants.targetList;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'azure';

    stage.interestingHealthProviderNames = []; // bypass the check for now; will change this later to ['azureService']

    if (!stage.credentials && $scope.application.defaultCredentials.azure) {
      stage.credentials = $scope.application.defaultCredentials.azure;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  });

