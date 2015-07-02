'use strict';

let angular = require('angular');


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
      executionDetailsUrl: 'app/scripts/modules/pipelines/config/stages/disableAsg/disableAsgExecutionDetails.html',
      executionStepLabelUrl: 'app/scripts/modules/pipelines/config/stages/disableAsg/disableAsgStepLabel.html',
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
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.state.regionsLoaded = true;
      });
    };

    $scope.targets = stageConstants.targetList;

    (function() {
      if ($scope.stage.credentials) {
        ctrl.accountUpdated();
      }
      if (!$scope.stage.target) {
        $scope.stage.target = $scope.targets[0].val;
      }
    })();

  })
  .name;

