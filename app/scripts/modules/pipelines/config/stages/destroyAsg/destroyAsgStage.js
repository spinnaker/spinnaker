'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?

module.exports = angular.module('spinnaker.pipelines.stage.destroyAsg')
  .config(function(pipelineConfigProvider, _) {
    pipelineConfigProvider.registerStage({
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      key: 'destroyAsg',
      controller: 'DestroyAsgStageCtrl',
      controllerAs: 'destroyAsgStageCtrl',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/destroyAsg/destroyAsgExecutionDetails.html',
      executionStepLabelUrl: 'scripts/modules/pipelines/config/stages/destroyAsg/destroyAsgStepLabel.html',
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
      ],
    });
  }).controller('DestroyAsgStageCtrl', function($scope, stage, accountService, _) {
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

  });

