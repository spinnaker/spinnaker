'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.aws.advancedSettings.editAsgAdvancedSettings.modal.controller', [
  require('../../../../utils/lodash.js'),
  require('../../../../tasks/monitor/taskMonitor.module.js'),
  require('../../../../tasks/taskExecutor.js'),
  require('../../configure/serverGroupCommandBuilder.service.js'),
])
  .controller('EditAsgAdvancedSettingsCtrl', function($scope, $modalInstance, taskMonitorService, taskExecutor, _,
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
  }).name;
