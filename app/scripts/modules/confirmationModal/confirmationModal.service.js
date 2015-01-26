'use strict';


angular.module('deckApp.confirmationModal.service', [
  'deckApp.tasks.monitor',
  'deckApp.account',
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
  .controller('ConfirmationModalCtrl', function($scope, $state, accountService, $modalInstance, params, taskMonitorService) {
    $scope.params = params;

    $scope.state = {
      submitting: false
    };

    params.taskMonitorConfig.modalInstance = $modalInstance;

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor(params.taskMonitorConfig);

    $scope.verification = {
      requireAccountEntry: accountService.challengeDestructiveActions(params.account),
      verifyAccount: ''
    };

    this.formDisabled = function () {
      return $scope.verification.requireAccountEntry && $scope.verification.verifyAccount !== params.account.toUpperCase();
    };

    this.confirm = function () {
      if (!this.formDisabled()) {
        $scope.taskMonitor.submit(params.submitMethod);
      }
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
