'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.cloneServerGroupStage', [
  require('../../../../../application/modal/platformHealthOverride.directive.js'),
  require('../../../../../account/account.service.js'),
  require('../../../../../../core/naming/naming.service.js'),
  require('./cloneServerGroupExecutionDetails.controller.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'cloneServerGroup',
      cloudProvider: 'aws',
      templateUrl: require('./cloneServerGroupStage.html'),
      executionDetailsUrl: require('./cloneServerGroupExecutionDetails.html'),
      executionStepLabelUrl: require('./cloneServerGroupStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'region', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'}
      ],
    });
  }).controller('awsCloneServerGroupStageCtrl', function($scope, accountService, namingService, stageConstants) {

    let stage = $scope.stage;

    $scope.viewState = {
      accountsLoaded: false,
    };

    accountService.listAccounts('aws').then((accounts) => {
      $scope.accounts = accounts;
      $scope.viewState.accountsLoaded = true;
    });

    this.cloneTargets = stageConstants.targetList;
    stage.target = stage.target || this.cloneTargets[0].val;
    stage.application = $scope.application.name;
    stage.cloudProvider = 'aws';
    stage.cloudProviderType = 'aws';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Amazon'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.aws) {
      stage.credentials = $scope.application.defaultCredentials.aws;
    }

    this.targetClusterUpdated = () => {
      if (stage.targetCluster) {
        let clusterName = namingService.parseServerGroupName(stage.targetCluster);
        stage.stack = clusterName.stack;
        stage.freeFormDetails = clusterName.freeFormDetails;
      } else {
        stage.stack = '';
        stage.freeFormDetails = '';
      }
    };

    $scope.$watch('stage.targetCluster', this.targetClusterUpdated);
  });

