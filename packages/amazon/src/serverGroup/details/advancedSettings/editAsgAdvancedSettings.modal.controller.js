'use strict';

import { module } from 'angular';

import { TaskExecutor, TaskMonitor } from '@spinnaker/core';
import { AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from '../../configure/serverGroupCommandBuilder.service';

export const AMAZON_SERVERGROUP_DETAILS_ADVANCEDSETTINGS_EDITASGADVANCEDSETTINGS_MODAL_CONTROLLER =
  'spinnaker.amazon.serverGroup.editAsgAdvancedSettings.modal.controller';
export const name = AMAZON_SERVERGROUP_DETAILS_ADVANCEDSETTINGS_EDITASGADVANCEDSETTINGS_MODAL_CONTROLLER; // for backwards compatibility
module(AMAZON_SERVERGROUP_DETAILS_ADVANCEDSETTINGS_EDITASGADVANCEDSETTINGS_MODAL_CONTROLLER, [
  AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
]).controller('EditAsgAdvancedSettingsCtrl', [
  '$scope',
  '$uibModalInstance',
  'application',
  'serverGroup',
  'awsServerGroupCommandBuilder',
  function ($scope, $uibModalInstance, application, serverGroup, awsServerGroupCommandBuilder) {
    $scope.command = awsServerGroupCommandBuilder.buildUpdateServerGroupCommand(serverGroup);

    $scope.serverGroup = serverGroup;

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Update Advanced Settings for ' + serverGroup.name,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      const job = [$scope.command];

      const submitMethod = function () {
        return TaskExecutor.executeTask({
          job: job,
          application: application,
          description: 'Update Advanced Settings for ' + serverGroup.name,
        });
      };

      $scope.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  },
]);
