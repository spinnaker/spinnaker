'use strict';

const angular = require('angular');

import { get } from 'lodash';
import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.rollback.controller', [SERVER_GROUP_WRITER])
  .controller('awsRollbackServerGroupCtrl', ['$scope', '$uibModalInstance', 'serverGroupWriter', 'application', 'serverGroup', 'previousServerGroup', 'disabledServerGroups', 'allServerGroups', function(
    $scope,
    $uibModalInstance,
    serverGroupWriter,
    application,
    serverGroup,
    previousServerGroup,
    disabledServerGroups,
    allServerGroups,
  ) {
    $scope.serverGroup = serverGroup;
    $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
    $scope.allServerGroups = allServerGroups.sort((a, b) => b.name.localeCompare(a.name));
    $scope.verification = {};

    var desired = serverGroup.capacity.desired;

    var rollbackType = 'EXPLICIT';

    if (allServerGroups.length === 0 && serverGroup.entityTags) {
      const previousServerGroup = get(serverGroup, 'entityTags.creationMetadata.value.previousServerGroup');
      if (previousServerGroup) {
        rollbackType = 'PREVIOUS_IMAGE';
        $scope.previousServerGroup = {
          name: previousServerGroup.name,
          imageName: previousServerGroup.imageName,
        };

        if (previousServerGroup.imageId && previousServerGroup.imageId !== previousServerGroup.imageName) {
          $scope.previousServerGroup.imageId = previousServerGroup.imageId;
        }

        const buildNumber = get(previousServerGroup, 'buildInfo.jenkins.number');
        if (buildNumber) {
          $scope.previousServerGroup.buildNumber = buildNumber;
        }
      }
    }

    if (desired < 10) {
      var healthyPercent = 100;
    } else if (desired < 20) {
      // accept 1 instance in an unknown state during rollback
      healthyPercent = 90;
    } else {
      healthyPercent = 95;
    }

    $scope.command = {
      rollbackType: rollbackType,
      rollbackContext: {
        rollbackServerGroupName: serverGroup.name,
        restoreServerGroupName: previousServerGroup ? previousServerGroup.name : undefined,
        targetHealthyRollbackPercentage: healthyPercent,
        delayBeforeDisableSeconds: 0,
      },
    };

    $scope.minHealthy = function(percent) {
      return Math.ceil((desired * percent) / 100);
    };

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Amazon'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function() {
      var command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }

      if (rollbackType === 'PREVIOUS_IMAGE') {
        // no need to validate when using an explicit image
        return true;
      }

      return command.rollbackContext.restoreServerGroupName !== undefined;
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Rollback ' + serverGroup.name,
      modalInstance: $uibModalInstance,
    });

    this.rollback = function() {
      if (!this.isValid()) {
        return;
      }

      var submitMethod = function() {
        return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };

    this.label = function(serverGroup) {
      if (!serverGroup) {
        return '';
      }

      if (!serverGroup.buildInfo || !serverGroup.buildInfo.jenkins || !serverGroup.buildInfo.jenkins.number) {
        return serverGroup.name;
      }

      return serverGroup.name + ' (build #' + serverGroup.buildInfo.jenkins.number + ')';
    };

    this.group = function(serverGroup) {
      return serverGroup.isDisabled ? 'Disabled Server Groups' : 'Enabled Server Groups';
    };
  }]);
