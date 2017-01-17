'use strict';

let angular = require('angular');

import {SERVER_GROUP_WRITER} from 'core/serverGroup/serverGroupWriter.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular.module('spinnaker.cf.serverGroup.details.resize.controller', [
  require('core/application/modal/platformHealthOverride.directive.js'),
  SERVER_GROUP_WRITER,
  TASK_MONITOR_BUILDER,
])
  .controller('cfResizeServerGroupCtrl', function($scope, $uibModalInstance, serverGroupWriter, taskMonitorBuilder,
                                                  application, serverGroup) {

    $scope.serverGroup = serverGroup;

    $scope.verification = {};

    $scope.command = {
      newSize: serverGroup.asg.desiredCapacity,
      memory: serverGroup.memory,
      disk: serverGroup.disk == 0 ? 1024 : serverGroup.disk,
    };


    if (application && application.attributes) {
      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }
      return command.newSize !== null;
    };

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Resizing ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

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

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
