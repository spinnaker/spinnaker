'use strict';

const angular = require('angular');

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.titus.serverGroup.details.resize.controller', [SERVER_GROUP_WRITER])
  .controller('titusResizeServerGroupCtrl', ['$scope', '$uibModalInstance', 'serverGroupWriter', 'application', 'serverGroup', function(
    $scope,
    $uibModalInstance,
    serverGroupWriter,
    application,
    serverGroup,
  ) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.capacity.min,
      max: serverGroup.capacity.max,
      desired: serverGroup.capacity.desired,
      newSize: null,
    };

    $scope.verification = {};

    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.capacity.min !== serverGroup.capacity.max;

    if (application && application.attributes) {
      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function() {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }
      return command.advancedMode
        ? command.min <= command.max && command.desired >= command.min && command.desired <= command.max
        : command.newSize !== null;
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Resizing ' + serverGroup.name,
      modalInstance: $uibModalInstance,
    });

    this.resize = function() {
      if (!this.isValid()) {
        return;
      }
      var capacity = { min: $scope.command.min, max: $scope.command.max, desired: $scope.command.desired };
      if (!$scope.command.advancedMode) {
        capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };
      }

      var submitMethod = function() {
        return serverGroupWriter.resizeServerGroup(serverGroup, application, {
          capacity: capacity,
          serverGroupName: serverGroup.name,
          instances: capacity.desired,
          interestingHealthProviderNames: $scope.command.interestingHealthProviderNames,
          region: serverGroup.region,
        });
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  }]);
