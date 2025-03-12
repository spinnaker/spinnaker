'use strict';

import { module } from 'angular';

import { TaskMonitor } from '@spinnaker/core';
import { ORACLE_COMMON_FOOTER_COMPONENT } from '../../../common/footer.component';

import { ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT } from './resizeCapacity.component';

export const ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER =
  'spinnaker.oracle.serverGroup.details.resize.controller';
export const name = ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER; // for backwards compatibility
module(ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZESERVERGROUP_CONTROLLER, [
  ORACLE_SERVERGROUP_DETAILS_RESIZE_RESIZECAPACITY_COMPONENT,
  ORACLE_COMMON_FOOTER_COMPONENT,
]).controller('oracleResizeServerGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  'application',
  'serverGroup',
  function ($scope, $uibModalInstance, application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.application = application;
    $scope.verification = {};
    $scope.command = {};
    $scope.formMethods = {};

    if (application && application.attributes) {
      if (application.attributes.platformHealthOnlyShowOverride && application.attributes.platformHealthOnly) {
        $scope.command.interestingHealthProviderNames = ['Oracle'];
      }

      $scope.command.platformHealthOnlyShowOverride = application.attributes.platformHealthOnlyShowOverride;
    }

    this.isValid = function () {
      if (!$scope.verification.verified) {
        return false;
      }
      return $scope.formMethods.formIsValid();
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: 'Resizing ' + serverGroup.name,
      modalInstance: $uibModalInstance,
    });

    this.resize = function () {
      this.submitting = true;
      if (!this.isValid()) {
        return;
      }

      $scope.taskMonitor.submit($scope.formMethods.submitMethod);
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  },
]);
