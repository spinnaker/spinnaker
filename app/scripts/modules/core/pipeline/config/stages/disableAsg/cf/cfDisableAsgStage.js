'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cf.disableAsgStage', [
  require('../../../../../application/modal/platformHealthOverride.directive.js'),
  require('../../../../../account/account.service.js'),
  require('../../stageConstants.js'),
  require('./disableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'cf',
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
        { type: 'requiredField', fieldName: 'zones', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('cfDisableAsgStageCtrl', function($scope, accountService, stageConstants) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      zonesLoaded: false
    };

    accountService.listAccounts('cf').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      accountService.getAccountDetails(stage.credentials).then(function(details) {
        stage.zones = [details.org];
      });
    };

    $scope.targets = stageConstants.targetList;

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'cf';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
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

  });

