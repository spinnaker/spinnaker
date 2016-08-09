'use strict';

//BEN_TODO: where is this defined?

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.azure.enableAsgStage', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
  require('./enableAsgExecutionDetails.controller.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      alias: 'enableAsg',
      cloudProvider: 'azure',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('./enableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('azureEnableAsgStageCtrl', function($scope, accountService, stageConstants) {
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

    ctrl.reset = () => {
      ctrl.accountUpdated();
      ctrl.resetSelectedCluster();
    };

    $scope.targets = stageConstants.targetList;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'azure';

    if (stage.isNew) {
      // bypass the health check for now; will change this later to ['azureService'] and we will also add back the check for $scope.application.attributes.platformHealthOnly
      stage.interestingHealthProviderNames = [];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.azure) {
      stage.credentials = $scope.application.defaultCredentials.azure;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.azure) {
      stage.regions.push($scope.application.defaultRegions.azure);
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

