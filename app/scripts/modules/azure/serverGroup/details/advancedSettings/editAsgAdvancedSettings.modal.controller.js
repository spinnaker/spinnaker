'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.advancedSettings.editAsgAdvancedSettings.modal.controller', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/task/monitor/taskMonitor.module.js'),
  require('../../../../core/task/taskExecutor.js'),
  require('../../configure/serverGroupCommandBuilder.service.js'),
])
  .controller('azureEditAsgAdvancedSettingsCtrl', function($scope, $modalInstance, taskMonitorService, taskExecutor, _,
                                                     application, serverGroup, azureServerGroupCommandBuilder) {

    $scope.command = azureServerGroupCommandBuilder.buildUpdateServerGroupCommand(serverGroup);

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
        modalInstance: $modalInstance,
        application: application,
        title: 'Update Advanced Settings for ' + serverGroup.name,
        submitMethod: submitMethod,
        forceRefreshEnabled: true,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $modalInstance.dismiss;
  });
