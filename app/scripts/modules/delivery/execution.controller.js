'use strict';

angular.module('deckApp.delivery')
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

    controller.isStageCurrent = function(stage) {
      return stage.name === $stateParams.stageName;
    };

  });

