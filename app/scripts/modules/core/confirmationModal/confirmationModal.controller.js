'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.confirmationModal.controller', [
    require('angular-ui-bootstrap'),
    require('../application/modal/platformHealthOverride.directive.js'),
    require('../task/monitor/taskMonitorService.js'),
  ])
  .controller('ConfirmationModalCtrl', function($scope, $modalInstance, taskMonitorService, params) {
    $scope.params = params;

    $scope.state = {
      submitting: false
    };

    if (params.taskMonitorConfig) {
      params.taskMonitorConfig.modalInstance = $modalInstance;

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(params.taskMonitorConfig);
    }

    if (params.taskMonitors) {
      params.taskMonitors.forEach(monitor => monitor.modalInstance = $modalInstance);
      $scope.taskMonitors = params.taskMonitors.map(taskMonitorService.buildTaskMonitor);
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
        } else {
          if (params.submitMethod) {
            params.submitMethod(params.interestingHealthProviderNames).then($modalInstance.close, showError);
          } else {
            $modalInstance.close();
          }
        }
      }
    };

    this.cancel = $modalInstance.dismiss;
  });
