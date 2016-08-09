'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.azure.disableAsgStage', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/pipeline/config/stages/stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'disableServerGroup',
      alias: 'disableAsg',
      cloudProvider: 'azure',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('../../../../core/pipeline/config/stages/disableAsg/templates/disableAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.'
        },
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
    });
  }).controller('azureDisableAsgStageCtrl', function($scope, accountService, stageConstants) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('azure').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });


    $scope.targets = stageConstants.targetList;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'azure';

    if (stage.isNew && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = []; // bypass the check for now; will change this later to ['azureService']
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

  });

