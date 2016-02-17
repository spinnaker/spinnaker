'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.disableAsgStage', [
  require('../../../../../application/modal/platformHealthOverride.directive.js'),
  require('../../stageConstants.js'),
  require('../../../../../account/account.service.js'),
  require('./disableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      cloudProvider: 'gce',
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
  }).controller('gceDisableAsgStageCtrl', function($scope, accountService, stageConstants) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      zonesLoaded: false
    };


    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.zones = {'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-c']};

    $scope.targets = stageConstants.targetList;

    stage.zones = stage.zones || [];
    stage.cloudProvider = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  });

