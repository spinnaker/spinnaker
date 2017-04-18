'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.findAmiStage', [
  PIPELINE_CONFIG_PROVIDER,
  require('./findAmiExecutionDetails.controller.js'),
  ACCOUNT_SERVICE,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'findImage',
      cloudProvider: 'gce',
      templateUrl: require('./findAmiStage.html'),
      executionDetailsUrl: require('./findAmiExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection'},
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials' }
      ]
    });
  }).controller('gceFindAmiStageCtrl', function($scope, accountService) {

    let stage = $scope.stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('gce').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.selectionStrategies = [{
      label: 'Largest',
      val: 'LARGEST',
      description: 'When multiple server groups exist, prefer the server group with the most instances'
    }, {
      label: 'Newest',
      val: 'NEWEST',
      description: 'When multiple server groups exist, prefer the newest'
    }, {
      label: 'Oldest',
      val: 'OLDEST',
      description: 'When multiple server groups exist, prefer the oldest'
    }, {
      label: 'Fail',
      val: 'FAIL',
      description: 'When multiple server groups exist, fail'
    }];

    stage.regions = stage.regions || [];
    stage.cloudProvider = 'gce';
    stage.selectionStrategy = stage.selectionStrategy || $scope.selectionStrategies[0].val;

    if (angular.isUndefined(stage.onlyEnabled)) {
      stage.onlyEnabled = true;
    }

    if (!stage.credentials && $scope.application.defaultCredentials.gce) {
      stage.credentials = $scope.application.defaultCredentials.gce;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.gce) {
      stage.regions.push($scope.application.defaultRegions.gce);
    }

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

