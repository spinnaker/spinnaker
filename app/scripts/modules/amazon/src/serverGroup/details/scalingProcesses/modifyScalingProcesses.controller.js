'use strict';

const angular = require('angular');
import _ from 'lodash';

import { TASK_EXECUTOR, TASK_MONITOR_BUILDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.details.aws.autoscaling.process.controller', [
  TASK_MONITOR_BUILDER,
  TASK_EXECUTOR,
  ])
  .controller('ModifyScalingProcessesCtrl', function($scope, $uibModalInstance, taskMonitorBuilder, taskExecutor, application, serverGroup, processes) {
    $scope.command = angular.copy(processes);
    $scope.serverGroup = serverGroup;
    $scope.verification = {};

    this.isValid = function () {
      if (!$scope.verification.verified) {
        return false;
      }
      return this.isDirty();
    };

    var currentlyEnabled = _.chain($scope.command).filter({enabled: true}).map('name').value(),
        currentlySuspended = _.chain($scope.command).filter({enabled: false}).map('name').value();

    this.isDirty = function () {
      var enabledSelections = _.chain($scope.command).filter({enabled: true}).map('name').value(),
        suspendedSelections = _.chain($scope.command).filter({enabled: false}).map('name').value(),
        toEnable = _.intersection(currentlySuspended, enabledSelections),
        toSuspend = _.intersection(currentlyEnabled, suspendedSelections);

      return !!(toEnable.length || toSuspend.length);
    };

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Update Auto Scaling Processes for ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = function () {
      var enabledSelections = _.chain($scope.command).filter({enabled: true}).map('name').value(),
          suspendedSelections = _.chain($scope.command).filter({enabled: false}).map('name').value(),
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

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
