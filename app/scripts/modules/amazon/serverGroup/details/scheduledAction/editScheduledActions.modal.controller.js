'use strict';

const angular = require('angular');

import { TASK_EXECUTOR, TASK_MONITOR_BUILDER } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.details.aws.scheduledActions.editScheduledActions.modal.controller', [
  TASK_MONITOR_BUILDER,
  TASK_EXECUTOR,
])
  .controller('EditScheduledActionsCtrl', function($scope, $uibModalInstance, taskMonitorBuilder, taskExecutor,
                                                   application, serverGroup) {
    $scope.command = {
      scheduledActions: serverGroup.scheduledActions.map((action) => {
        return {
          recurrence: action.recurrence,
          minSize: action.minSize,
          maxSize: action.maxSize,
          desiredCapacity: action.desiredCapacity,
        };
      }),
    };

    $scope.serverGroup = serverGroup;

    this.addScheduledAction = () => {
      $scope.command.scheduledActions.push({});
    };

    this.removeScheduledAction = (index) => {
      $scope.command.scheduledActions.splice(index, 1);
    };

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Update Scheduled Actions for ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      var job = [
        {
          type: 'upsertAsgScheduledActions',
          asgs: [{asgName: serverGroup.name, region: serverGroup.region}],
          scheduledActions: $scope.command.scheduledActions,
          credentials: serverGroup.account,
        }
      ];

      var submitMethod = function() {
        return taskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Scheduled Actions for ' + serverGroup.name
        });
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
