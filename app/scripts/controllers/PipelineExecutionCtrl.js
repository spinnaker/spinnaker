'use strict';

angular.module('deckApp')
  .controller('PipelineExecutionCtrl', function($scope, $location, $stateParams) {
    this.setActiveStage = function(idx) {
      // done this way mostly to move the caret to the correct place
      $scope.activeStageIndex = idx;
      $scope.activeStage = $scope.execution.stages[idx];
    };


    this.currentExecution = function() {
      return $stateParams.executionId === $scope.execution.id;
    };

    this.currentStage = function(stage) {
      return $stateParams.stageName === stage.name;
    };

    $scope.show = $scope.execution.isRunning ||
      $location.search()[$scope.execution.id];
    if ($scope.show) {
      $location.search($scope.execution.id, $scope.show);
    }
    this.toggle = function() {
      $scope.show = !$scope.show;
      $location.search($scope.execution.id, $scope.show);
    };
  });
