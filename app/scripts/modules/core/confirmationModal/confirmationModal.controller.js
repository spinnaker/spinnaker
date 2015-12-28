'use strict';

let angular = require('angular');

require('./confirmationModal.less');

module.exports = angular
  .module('spinnaker.core.confirmationModal.controller', [
    require('angular-ui-bootstrap'),
    require('../application/modal/platformHealthOverride.directive.js'),
    require('../task/monitor/taskMonitorService.js'),
    require('../account/account.module.js'),
  ])
  .controller('ConfirmationModalCtrl', function($scope, $modalInstance, accountService, params, taskMonitorService) {
    $scope.params = params;

    $scope.state = {
      submitting: false
    };

    if (params.taskMonitorConfig) {
      params.taskMonitorConfig.modalInstance = $modalInstance;

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(params.taskMonitorConfig);
    }

    $scope.verification = {
      requireVerification: params.verificationLabel && params.textToVerify !== undefined,
      userVerification: '',
      textToVerify: params.account ? params.account.toUpperCase() : params.textToVerify
    };

    if (params.account && !params.requireVerification) {
      accountService.challengeDestructiveActions(params.account).then((challenge) => {
        $scope.verification.requireVerification = challenge;
      });
    }

    this.formDisabled = function () {
      return $scope.verification.requireVerification && $scope.verification.userVerification !== $scope.verification.textToVerify;
    };

    function showError(exception) {
      $scope.state.error = true;
      $scope.state.submitting = false;
      $scope.errorMessage = exception;
    }

    this.confirm = function () {
      if (!this.formDisabled()) {
        if ($scope.taskMonitor) {
          $scope.taskMonitor.submit(() => { return params.submitMethod(params.interestingHealthProviderNames); });
        } else {
          if (params.submitMethod) {
            $scope.state.submitting = true;
            params.submitMethod(params.interestingHealthProviderNames).then($modalInstance.close, showError);
          } else {
            $modalInstance.close();
          }
        }
      }
    };

    this.cancel = $modalInstance.dismiss;
  });
