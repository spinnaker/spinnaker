'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.advancedSettings.editAsgAdvancedSettings.modal.controller', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/task/monitor/taskMonitor.module.js'),
  require('../../../../core/task/taskExecutor.js'),
  require('../../configure/serverGroupCommandBuilder.service.js'),
])
  .controller('EditAsgAdvancedSettingsCtrl', function($scope, $uibModalInstance, taskMonitorService, taskExecutor, _,
                                                     application, serverGroup, awsServerGroupCommandBuilder) {

    $scope.command = awsServerGroupCommandBuilder.buildUpdateServerGroupCommand(serverGroup);

    $scope.serverGroup = serverGroup;

    this.submit = () => {
      var job = [ $scope.command ];

      var submitMethod = function() {
        return taskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Advanced Settings for ' + serverGroup.name
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Update Advanced Settings for ' + serverGroup.name,
        onTaskComplete: application.serverGroups.refresh,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  });
