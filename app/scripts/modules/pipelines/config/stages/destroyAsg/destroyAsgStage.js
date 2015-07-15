'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?

require('./destroyAsgStage.html');
require('./destroyAsgExecutionDetails.html');
require('./destroyAsgStepLabel.html');

module.exports = angular.module('spinnaker.pipelines.stage.destroyAsgStage', [
  require('utils/lodash.js'),
  require('../stageConstants.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      key: 'destroyAsg',
      controller: 'DestroyAsgStageCtrl',
      controllerAs: 'destroyAsgStageCtrl',
      templateUrl: require('./destroyAsgStage.html'),
      executionDetailsUrl: require('./destroyAsgExecutionDetails.html'),
      executionStepLabelUrl: require('./destroyAsgStepLabel.html'),
      validators: [
        {
          type: 'targetImpedance',
          message: 'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.'
        },
      ],
    });
  }).controller('DestroyAsgStageCtrl', function($scope, stage, accountService, stageConstants, _) {
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

