'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.modifyScalingProcessStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Modify Scaling Process',
      description: 'Suspend/Resume Scaling Processes',
      key: 'modifyAwsScalingProcess',
      alias: 'modifyScalingProcess',
      controller: 'ModifyScalingProcessStageCtrl',
      controlelrAs: 'modifyScalingProcessStageCtrl',
      templateUrl: require('./modifyScalingProcessStage.html'),
      executionDetailsUrl: require('./modifyScalingProcessExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'target', },
        { type: 'requiredField', fieldName: 'action', },
        { type: 'requiredField', fieldName: 'regions', },
        { type: 'requiredField', fieldName: 'processes', },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account'},
      ],
      cloudProvider: 'aws',
      strategy: true,
    });
  }).controller('ModifyScalingProcessStageCtrl', function($scope, stage, accountService, stageConstants, _) {
    $scope.stage = stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('aws').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.regions = ['us-east-1', 'us-west-1', 'eu-west-1', 'us-west-2'];

    $scope.accountUpdated = function() {
      accountService.getRegionsForAccount(stage.credentials).then(function(regions) {
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

    stage.processes = stage.processes || [];
    stage.regions = stage.regions || [];
    stage.action = stage.action || $scope.actions[0].val;
    stage.target = stage.target || $scope.targets[0].val;
    stage.cloudProvider = 'aws';

    if (!stage.credentials && $scope.application.defaultCredentials.aws) {
      stage.credentials = $scope.application.defaultCredentials.aws;
    }
    if (!stage.regions.length && $scope.application.defaultRegions.aws) {
      stage.regions.push($scope.application.defaultRegions.aws);
    }

    if (stage.credentials) {
      $scope.accountUpdated();
    }

    $scope.toggleProcess = function(process) {
      if (!stage.processes) {
        stage.processes = [];
      }
      var idx = stage.processes.indexOf(process);
      if (idx > -1) {
        stage.processes.splice(idx, 1);
      } else {
        stage.processes.push(process);
      }
    };

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

