'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.modifyScalingProcess')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Modify Scaling Process',
      description: 'Suspend/Resume Scaling Processes',
      key: 'modifyScalingProcess',
      controller: 'ModifyScalingProcessStageCtrl',
      controlelrAs: 'modifyScalingProcessStageCtrl',
      template: require('./modifyScalingProcessStage.html'),
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/modifyScalingProcess/modifyScalingProcessExecutionDetails.html',
    });
  }).controller('ModifyScalingProcessStageCtrl', function($scope, stage, accountService, _) {
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

    $scope.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.stage.credentials).then(function(regions) {
        $scope.regions = _.map(regions, function(v) { return v.name; });
        $scope.regionsLoaded = true;
      });
    };

    $scope.targets = stageConstants.targetList;

    $scope.actions = [
      {
        label: 'Suspend',
        val: 'suspend'
      },
      {
        label: 'Resume',
        val: 'resume'
      }
    ];
    $scope.processes = [
      'Launch', 'Terminate', 'AddToLoadBalancer', 'AlarmNotification', 'AZRebalance', 'HealthCheck', 'ReplaceUnhealthy', 'ScheduledActions'
    ];

    (function() {
      if (!$scope.stage.processes) {
        $scope.stage.processes = [];
      }
      if ($scope.stage.credentials) {
        $scope.accountUpdated();
      }
      if (!$scope.stage.action) {
        $scope.stage.action = $scope.actions[0].val;
      }
      if (!$scope.stage.target) {
        $scope.stage.target = $scope.targets[0].val;
      }
    })();

    $scope.toggleProcess = function(process) {
      if (!$scope.stage.processes) {
        $scope.stage.processes = [];
      }
      var idx = $scope.stage.processes.indexOf(process);
      if (idx > -1) {
        $scope.stage.processes.splice(idx, 1);
      } else {
        $scope.stage.processes.push(process);
      }
    };

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

