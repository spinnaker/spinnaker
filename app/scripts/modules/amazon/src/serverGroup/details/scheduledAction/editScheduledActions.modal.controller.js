'use strict';

const angular = require('angular');

import { TaskExecutor, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.scheduledActions.editScheduledActions.modal.controller', [])
  .controller('EditScheduledActionsCtrl', ['$scope', '$uibModalInstance', 'application', 'serverGroup', function($scope, $uibModalInstance, application, serverGroup) {
    $scope.command = {
      scheduledActions: serverGroup.scheduledActions.map(action => {
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

    this.removeScheduledAction = index => {
      $scope.command.scheduledActions.splice(index, 1);
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Update Scheduled Actions for ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      var job = [
        {
          type: 'upsertAsgScheduledActions',
          asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
          scheduledActions: $scope.command.scheduledActions,
          credentials: serverGroup.account,
        },
      ];

      var submitMethod = function() {
        return TaskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Scheduled Actions for ' + serverGroup.name,
        });
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  }]);
