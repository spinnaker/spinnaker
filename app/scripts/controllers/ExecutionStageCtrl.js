'use strict';

angular.module('deckApp')
  .controller('ExecutionStageCtrl', function($scope, pipelines) {
    pipelines.getCurrentStage().then(function(stage) {
      $scope.stage = stage;
      var subscription = pipelines.subscribeToCurrentStage(function(stage) {
        $scope.stage = stage;
        $scope.$on('$destroy', function() {
          subscription.dispose();
        });
      });
    });
  });

