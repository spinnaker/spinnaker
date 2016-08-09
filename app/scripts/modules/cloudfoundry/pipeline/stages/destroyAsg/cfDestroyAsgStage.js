'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.destroyAsgStage', [
  require('../../../../core/account/account.service.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'destroyServerGroup',
      cloudProvider: 'cf',
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
  }).controller('cfDestroyAsgStageCtrl', function($scope, accountService, stageConstants) {
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

    if (!stage.credentials && $scope.application.defaultCredentials.cf) {
      stage.credentials = $scope.application.defaultCredentials.cf;
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  });

