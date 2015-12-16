'use strict';

let angular = require('angular');

require('./confirmationModal.less');

module.exports = angular.module('spinnaker.core.confirmationModal.service', [
  require('../utils/lodash.js'),
  require('../application/modal/platformHealthOverride.directive.js'),
  require('../task/monitor/taskMonitor.module.js'),
  require('../account/account.module.js'),
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
])
  .factory('confirmationModalService', function($uibModal, $sce) {
    var defaults = {
      buttonText: 'Confirm',
      cancelButtonText: 'Cancel'
    };

    function confirm(params) {
      params = angular.extend(angular.copy(defaults), params);

      if (params.body) {
        params.body = $sce.trustAsHtml(params.body);
      }

      var modalArgs = {
        templateUrl: require('./confirm.html'),
        controller: 'ConfirmationModalCtrl as ctrl',
        resolve: {
          params: function() {
            return params;
          }
        }
      };

      if (params.size) {
        modalArgs.size = params.size;
      }
      return $uibModal.open(modalArgs).result;
    }

    return {
      confirm: confirm
    };
  })
  .controller('ConfirmationModalCtrl', function($scope, $state, $modalInstance, accountService, params, taskMonitorService) {
    $scope.params = params;

    $scope.state = {
      submitting: false
    };

    if (params.taskMonitorConfig) {
      params.taskMonitorConfig.modalInstance = $modalInstance;

      $scope.taskMonitor = taskMonitorService.buildTaskMonitor(params.taskMonitorConfig);
    }

    $scope.verification = {
      requireAccountEntry: false,
      verifyAccount: ''
    };

    accountService.challengeDestructiveActions(params.account).then((challenge) => {
      $scope.verification.requireAccountEntry = challenge;
    });

    this.formDisabled = function () {
      return $scope.verification.requireAccountEntry && $scope.verification.verifyAccount !== params.account.toUpperCase();
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

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
