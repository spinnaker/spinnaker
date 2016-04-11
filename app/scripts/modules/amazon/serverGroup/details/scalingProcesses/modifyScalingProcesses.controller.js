'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.autoscaling.process.controller', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/task/monitor/taskMonitor.module.js'),
  require('../../../../core/task/taskExecutor.js'),
  ])
  .controller('ModifyScalingProcessesCtrl', function($scope, $uibModalInstance, taskMonitorService, taskExecutor, application, serverGroup, processes, _) {
    $scope.command = angular.copy(processes);
    $scope.serverGroup = serverGroup;
    $scope.verification = {};

    this.isValid = function () {
      if (!$scope.verification.verified) {
        return false;
      }
      return this.isDirty();
    };

    var currentlyEnabled = _($scope.command).filter({enabled: true}).pluck('name').valueOf(),
        currentlySuspended = _($scope.command).filter({enabled: false}).pluck('name').valueOf();

    this.isDirty = function () {
      var enabledSelections = _($scope.command).filter({enabled: true}).pluck('name').valueOf(),
        suspendedSelections = _($scope.command).filter({enabled: false}).pluck('name').valueOf(),
        toEnable = _.intersection(currentlySuspended, enabledSelections),
        toSuspend = _.intersection(currentlyEnabled, suspendedSelections);

      return !!(toEnable.length || toSuspend.length);
    };

    this.submit = function () {
      var enabledSelections = _($scope.command).filter({enabled: true}).pluck('name').valueOf(),
          suspendedSelections = _($scope.command).filter({enabled: false}).pluck('name').valueOf(),
          toEnable = _.intersection(currentlySuspended, enabledSelections),
          toSuspend = _.intersection(currentlyEnabled, suspendedSelections);

      var job = [];
      if (toEnable.length) {
        job.push({
          type: 'modifyScalingProcess',
          action: 'resume',
          processes: toEnable,
          asgName: serverGroup.name,
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          cloudProvider: 'aws',
          reason: $scope.command.reason,
        });
      }
      if (toSuspend.length) {
        job.push({
          type: 'modifyScalingProcess',
          action: 'suspend',
          processes: toSuspend,
          asgName: serverGroup.name,
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          cloudProvider: 'aws',
          reason: $scope.command.reason,
        });
      }

      var submitMethod = function() {
        return taskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Auto Scaling Processes for ' + serverGroup.name
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Update Auto Scaling Processes for ' + serverGroup.name,
        onTaskComplete: application.serverGroups.refresh,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
