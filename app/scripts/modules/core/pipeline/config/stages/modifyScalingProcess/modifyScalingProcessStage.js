'use strict';

let angular = require('angular');
import {StageConstants} from 'core/pipeline/config/stages/stageConstants';

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
  }).controller('ModifyScalingProcessStageCtrl', function($scope, stage, accountService) {
    $scope.stage = stage;

    $scope.state = {
      accounts: false,
      regionsLoaded: false
    };

    accountService.listAccounts('aws').then(function (accounts) {
      $scope.accounts = accounts;
      $scope.state.accounts = true;
    });

    $scope.targets = StageConstants.TARGET_LIST;

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

