'use strict';

//BEN_TODO: where is this defined?

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.enableAsgStage', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/account/account.service.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
  require('./enableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'cf',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('./enableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ]
    });
  }).controller('cfEnableAsgStageCtrl', function($scope, accountService, stageConstants) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('cf').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      accountService.getAccountDetails(stage.credentials).then(function(details) {
        stage.regions = [details.org];
      });
    };

    $scope.targets = stageConstants.targetList;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'cf';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

