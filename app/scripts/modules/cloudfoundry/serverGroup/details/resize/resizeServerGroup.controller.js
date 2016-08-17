'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.serverGroup.details.resize.controller', [
  require('../../../../core/application/modal/platformHealthOverride.directive.js'),
  require('../../../../core/serverGroup/serverGroup.write.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js')
])
  .controller('cfResizeServerGroupCtrl', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorService,
                                                   application, serverGroup) {

    $scope.serverGroup = serverGroup;

    $scope.verification = {};

    $scope.command = {
      newSize: serverGroup.asg.desiredCapacity,
      memory: serverGroup.memory,
      disk: serverGroup.disk == 0 ? 1024 : serverGroup.disk,
    };


    if (application && application.attributes) {
      if (application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Cloud Foundry'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }
      return command.newSize !== null;
    };

    this.resize = function () {
      if (!this.isValid()) {
        return;
      }
      var newSize = $scope.command.newSize;
      var memory = $scope.command.memory;
      var disk = $scope.command.disk;

      var submitMethod = function() {
        return serverGroupWriter.resizeServerGroup(serverGroup, application, {
          capacity: { min: newSize, max: newSize, desired: newSize },
          serverGroupName: serverGroup.name,
          targetSize: newSize, // TODO(GLT): Unify on this or capacity
          memory: memory,
          disk: disk,
          region: serverGroup.region,
          interestingHealthProviderNames: $scope.command.interestingHealthProviderNames,
        });
      };

      var taskMonitorConfig = {
        modalInstance: $uibModalInstance,
        application: application,
        title: 'Resizing ' + serverGroup.name,
        onTaskComplete: application.serverGroups.refresh,
      };

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
