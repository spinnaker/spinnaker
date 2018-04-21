'use strict';

const angular = require('angular');

import { AccountService, StageConstants } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cf.pipeline.stage.enableAsgStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'cf',
      templateUrl: require('./enableAsgStage.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('cfEnableAsgStageCtrl', function($scope) {
    var ctrl = this;

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false,
    };

    AccountService.listAccounts('cf').then(function(accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    ctrl.accountUpdated = function() {
      AccountService.getAccountDetails(stage.credentials).then(function(details) {
        stage.regions = [details.org];
      });
    };

    $scope.targets = StageConstants.TARGET_LIST;

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

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });
