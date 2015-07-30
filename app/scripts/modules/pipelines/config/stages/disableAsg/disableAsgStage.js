'use strict';

let angular = require('angular');

require('./disableAsgStage.html');
require('./disableAsgExecutionDetails.html');
require('./disableAsgStepLabel.html');

//BEN_TODO lodash
module.exports = angular.module('spinnaker.pipelines.stage.disableAsgStage', [
  require('utils/lodash.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Disable Server Group',
      description: 'Disables a server group',
      key: 'disableAsg',
      controller: 'DisableAsgStageCtrl',
      controllerAs: 'disableAsgStageCtrl',
      templateUrl: require('./disableAsgStage.html'),
      executionDetailsUrl: require('./disableAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./disableAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.'
        },
      ],
    });
  }).controller('DisableAsgStageCtrl', function($scope, stage, accountService, stageConstants, _) {
    var ctrl = this;

    $scope.stage = stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts().then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.state.regionsLoaded = true;
      });
    };

    $scope.targets = stageConstants.targetList;

    stage.regions = stage.regions || [];

    if (!stage.credentials && $scope.application.defaultCredentials) {
      stage.credentials = $scope.application.defaultCredentials;
    }
    if (!stage.regions.length && $scope.application.defaultRegion) {
      stage.regions.push($scope.application.defaultRegion);
    }

    if (stage.credentials) {
      ctrl.accountUpdated();
    }
    if (!stage.target) {
      stage.target = $scope.targets[0].val;
    }

  })
  .name;

