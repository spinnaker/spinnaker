'use strict';

angular.module('spinnaker.pipelines.stage.modifyScalingProcess')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Modify Scaling Process',
      description: 'Suspend/Resume Scaling Processes',
      key: 'modifyScalingProcess',
      controller: 'ModifyScalingProcessStageCtrl',
      controlelrAs: 'modifyScalingProcessStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/modifyScalingProcess/modifyScalingProcessStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/modifyScalingProcess/modifyScalingProcessExecutionDetails.html',
    });
  }).controller('ModifyScalingProcessStageCtrl', function($scope, stage, accountService, stageConstants) {
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

    if (!stage.credentials && $scope.application.defaultCredentials) {
      stage.credentials = $scope.application.defaultCredentials;
    }
    if (!stage.regions.length && $scope.application.defaultRegion) {
      stage.regions.push($scope.application.defaultRegion);
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
        stage.processes.splice(idx,1);
      } else {
        stage.processes.push(process);
      }
    };

    $scope.$watch('stage.credentials', $scope.accountUpdated);
  });

