'use strict';

angular.module('deckApp.pipelines.stage.wait')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      controller: 'WaitStageCtrl',
      controllerAs: 'waitStageCtrl',
      templateUrl: 'scripts/modules/pipelines/config/stages/wait/waitStage.html',
      executionDetailsUrl: 'scripts/modules/pipelines/config/stages/wait/waitExecutionDetails.html',
    });
  })
  .controller('WaitStageCtrl', function($scope, stage, authenticationService) {
    $scope.stage = stage;

    $scope.viewState = {
      loading: false
    };
  });
