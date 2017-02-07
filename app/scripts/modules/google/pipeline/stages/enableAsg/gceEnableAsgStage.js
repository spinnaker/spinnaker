'use strict';

let angular = require('angular');
import {StageConstants} from 'core/pipeline/config/stages/stageConstants';

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.enableAsgStage', [
  require('core/pipeline/config/pipelineConfigProvider'),
  require('core/application/modal/platformHealthOverride.directive.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'enableServerGroup',
      cloudProvider: 'gce',
      templateUrl: require('./enableAsgStage.html'),
      executionDetailsUrl: require('core/pipeline/config/stages/enableAsg/templates/enableAsgExecutionDetails.template.html'),
      executionStepLabelUrl: require('./enableAsgStepLabel.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ]
    });
  }).controller('gceEnableAsgStageCtrl', function($scope, accountService) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.targets = StageConstants.TARGET_LIST;

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'gce';

    if (stage.isNew && $scope.application.attributes.platformHealthOnlyShowOverride && $scope.application.attributes.platformHealthOnly) {
      stage.interestingHealthProviderNames = ['Google'];
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.gce) {
      stage.regions.push($scope.application.defaultRegions.gce);
    }

    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

