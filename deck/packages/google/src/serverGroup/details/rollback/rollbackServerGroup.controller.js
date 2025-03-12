'use strict';

import { module } from 'angular';

import { SERVER_GROUP_WRITER, TaskMonitor } from '@spinnaker/core';
import { GOOGLE_COMMON_FOOTER_DIRECTIVE } from '../../../common/footer.directive';

export const GOOGLE_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER =
  'spinnaker.google.serverGroup.details.rollback.controller';
export const name = GOOGLE_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_ROLLBACK_ROLLBACKSERVERGROUP_CONTROLLER, [
  SERVER_GROUP_WRITER,
  GOOGLE_COMMON_FOOTER_DIRECTIVE,
]).controller('gceRollbackServerGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  'serverGroupWriter',
  'application',
  'serverGroup',
  'disabledServerGroups',
  function ($scope, $uibModalInstance, serverGroupWriter, application, serverGroup, disabledServerGroups) {
    $scope.serverGroup = serverGroup;
    $scope.disabledServerGroups = disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name));
    $scope.verification = {};

    $scope.command = {
      rollbackType: 'EXPLICIT',
      rollbackContext: {
        rollbackServerGroupName: serverGroup.name,
      },
    };

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Google'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      const command = $scope.command;
      if (!$scope.verification.verified) {
        return false;
      }

      return command.rollbackContext.restoreServerGroupName !== undefined;
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Rollback ' + serverGroup.name,
      modalInstance: $uibModalInstance,
    });

    this.rollback = function () {
      this.submitting = true;
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
  },
]);
