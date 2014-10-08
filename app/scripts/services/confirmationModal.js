'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('confirmationModalService', function($modal) {
    var defaults = {
      buttonText: 'Confirm'
    };

    function confirm(params) {
      params = angular.extend(angular.copy(defaults), params);

      var modalArgs = {
        templateUrl: 'views/modal/confirm.html',
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

    this.confirm = function () {
      $scope.taskMonitor.submit(params.submitMethod);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
