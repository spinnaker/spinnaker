'use strict';

angular.module('deckApp.delivery.execution.controller', [
  'ui.router',
  'deckApp.confirmationModal.service',
  'deckApp.delivery.executions.service',
])
  .controller('execution', function($scope, $stateParams, $state, confirmationModalService, executionsService, settings) {
    var controller = this;

    controller.showDetails = function() {
      return $scope.execution.id === $stateParams.executionId &&
        $state.includes('**.execution.**');
    };

    controller.go = function() {
      $state.go('.execution', {
        executionId: $scope.execution.id,
      });
    };

    controller.pipelinesUrl = [settings.gateUrl, 'pipelines/'].join('/');

    controller.executionInState = function() {
      return $stateParams.executionId && $state.includes('**.execution.**');
    };

    controller.executionIsCurrent = function() {
      return controller.executionInState() && $scope.execution.id === $stateParams.executionId;
    };

    controller.deleteExecution = function() {
      confirmationModalService.confirm({
        header: 'Really delete execution?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the execution history.</p>',
        submitMethod: function() {
          return executionsService.deleteExecution($scope.application, $scope.execution.id);
        }
      });
    };

  });

