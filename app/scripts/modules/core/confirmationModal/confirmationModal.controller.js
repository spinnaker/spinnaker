'use strict';

const angular = require('angular');

import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular
  .module('spinnaker.core.confirmationModal.controller', [
    require('angular-ui-bootstrap'),
    require('../application/modal/platformHealthOverride.directive.js'),
    TASK_MONITOR_BUILDER,
  ])
  .controller('ConfirmationModalCtrl', function($scope, $uibModalInstance, taskMonitorBuilder, params) {
    $scope.params = params;

    $scope.state = {
      submitting: false
    };

    if (params.taskMonitorConfig) {
      params.taskMonitorConfig.modalInstance = $uibModalInstance;

      $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor(params.taskMonitorConfig);
    }

    if (params.taskMonitors) {
      params.taskMonitors.forEach(monitor => monitor.modalInstance = $uibModalInstance);
      $scope.taskMonitors = params.taskMonitors.map((config) => taskMonitorBuilder.buildTaskMonitor(config));
    }


    $scope.verification = {
      required: !!params.account || (params.verificationLabel && params.textToVerify !== undefined),
      toVerify: params.textToVerify,
    };

    this.formDisabled = () => {
      return $scope.state.submitting || ($scope.verification.required && !$scope.verification.verified);
    };

    function showError(exception) {
      $scope.state.error = true;
      $scope.state.submitting = false;
      $scope.errorMessage = exception;
    }

    this.confirm = function () {
      if (!this.formDisabled()) {
        $scope.state.submitting = true;
        if ($scope.taskMonitors) {
          $scope.taskMonitors.forEach(monitor => monitor.callPreconfiguredSubmit({reason: params.reason}));
        } else if ($scope.taskMonitor) {
          $scope.taskMonitor.submit(() => { return params.submitMethod({interestingHealthProviderNames: params.interestingHealthProviderNames, reason: params.reason}); });
        } else if (params.submitJustWithReason) {
          params.submitMethod(params.reason).then($uibModalInstance.close, showError);
        } else {
          if (params.submitMethod) {
            params.submitMethod(params.interestingHealthProviderNames).then($uibModalInstance.close, showError);
          } else {
            $uibModalInstance.close();
          }
        }
      }
    };

    this.cancel = $uibModalInstance.dismiss;
  });
