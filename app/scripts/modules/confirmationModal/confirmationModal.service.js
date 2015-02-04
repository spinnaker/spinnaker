'use strict';


angular.module('deckApp.confirmationModal.service', [
  'ui.bootstrap',
  'deckApp.tasks.monitor',
  'deckApp.account',
  'ui.router'
])
  .factory('confirmationModalService', function($modal) {
    var defaults = {
      buttonText: 'Confirm'
    };

    function confirm(params) {
      params = angular.extend(angular.copy(defaults), params);

      var modalArgs = {
        templateUrl: 'scripts/modules/confirmationModal/confirm.html',
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
      return $modal.open(modalArgs).result;
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
      requireAccountEntry: accountService.challengeDestructiveActions(params.account),
      verifyAccount: ''
    };

    this.formDisabled = function () {
      return $scope.verification.requireAccountEntry && $scope.verification.verifyAccount !== params.account.toUpperCase();
    };

    function showError(exception) {
      $scope.state.error = true;
      $scope.errorMessage = exception;
    }

    this.confirm = function () {
      if (!this.formDisabled()) {
        if ($scope.taskMonitor) {
          $scope.taskMonitor.submit(params.submitMethod);
        } else {
          if (params.submitMethod) {
            params.submitMethod().then($modalInstance.close, showError);
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
