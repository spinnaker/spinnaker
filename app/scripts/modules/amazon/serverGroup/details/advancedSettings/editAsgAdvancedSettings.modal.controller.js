'use strict';

import {TASK_EXECUTOR} from 'core/task/taskExecutor';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.advancedSettings.editAsgAdvancedSettings.modal.controller', [
  TASK_MONITOR_BUILDER,
  TASK_EXECUTOR,
  require('../../configure/serverGroupCommandBuilder.service.js'),
])
  .controller('EditAsgAdvancedSettingsCtrl', function($scope, $uibModalInstance, taskMonitorBuilder, taskExecutor,
                                                      application, serverGroup, awsServerGroupCommandBuilder) {

    $scope.command = awsServerGroupCommandBuilder.buildUpdateServerGroupCommand(serverGroup);

    $scope.serverGroup = serverGroup;

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Update Advanced Settings for ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      var job = [ $scope.command ];

      var submitMethod = function() {
        return taskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Advanced Settings for ' + serverGroup.name
        });
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
