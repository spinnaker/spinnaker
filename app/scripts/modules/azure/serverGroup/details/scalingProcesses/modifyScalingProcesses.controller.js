'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.autoscaling.process.controller', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/task/monitor/taskMonitor.module.js'),
  require('../../../../core/task/taskExecutor.js'),
])
  .controller('azureModifyScalingProcessesCtrl', function($scope, $modalInstance, taskMonitorService, taskExecutor, application, serverGroup, processes, _) {
    $scope.command = angular.copy(processes);
    $scope.serverGroup = serverGroup;

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
          cloudProvider: 'azure',
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
          cloudProvider: 'azure',
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
        modalInstance: $modalInstance,
        application: application,
        title: 'Update Auto Scaling Processes for ' + serverGroup.name,
        submitMethod: submitMethod,
        forceRefreshEnabled: true,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $modalInstance.dismiss;
  });
