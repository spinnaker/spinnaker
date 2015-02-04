'use strict';

angular.module('deckApp.delivery.execution.controller', [
  'ui.router'
])
  .controller('execution', function($scope, $stateParams, $state) {
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

    controller.executionInState = function() {
      return $stateParams.executionId && $state.includes('**.execution.**');
    };

    controller.executionIsCurrent = function() {
      return controller.executionInState() && $scope.execution.id === $stateParams.executionId;
    };

  });

