'use strict';

import { module } from 'angular';

import { get } from 'lodash';
import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';

export const AMAZON_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER =
  'spinnaker.amazon.serverGroup.details.rollback.controller';
export const name = AMAZON_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER, [SERVER_GROUP_WRITER]).controller(
  'awsRollbackServerGroupCtrl',
  [
    '$scope',
    '$uibModalInstance',
    'serverGroupWriter',
    'application',
    'serverGroup',
    'previousServerGroup',
    'disabledServerGroups',
    'allServerGroups',
    function (
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

      const desired = serverGroup.capacity.desired;

      let rollbackType = 'EXPLICIT';

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

      let healthyPercent;
      if (desired < 10) {
        healthyPercent = 100;
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

      $scope.minHealthy = function (percent) {
        return Math.ceil((desired * percent) / 100);
      };

      if (application && application.attributes) {
        if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
          $scope.command.interestingHealthProviderNames = ['Amazon'];
        }

        $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
      }

      this.isValid = function () {
        const command = $scope.command;
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

      this.rollback = function () {
        if (!this.isValid()) {
          return;
        }

        const submitMethod = function () {
          return serverGroupWriter.rollbackServerGroup(serverGroup, application, $scope.command);
        };

        $scope.taskMonitor.submit(submitMethod);
      };

      this.cancel = function () {
        $uibModalInstance.dismiss();
      };

      this.label = function (serverGroup) {
        if (!serverGroup) {
          return '';
        }

        if (!serverGroup.buildInfo || !serverGroup.buildInfo.jenkins || !serverGroup.buildInfo.jenkins.number) {
          return serverGroup.name;
        }

        return serverGroup.name + ' (build #' + serverGroup.buildInfo.jenkins.number + ')';
      };

      this.group = function (serverGroup) {
        return serverGroup.isDisabled ? 'Disabled Server Groups' : 'Enabled Server Groups';
      };
    },
  ],
);
