'use strict';

angular.module('deckApp.pipelines.config.controller', [
  'ui.router',
  'deckApp.pipelines.config.service'
])
  .controller('PipelineConfigCtrl', function($scope, $stateParams, pipelineConfigService) {

    $scope.state = {
      pipelinesLoaded: false
    };

    pipelineConfigService.getPipelinesForApplication($stateParams.application).then(function(pipelines) {
      $scope.application.pipelines = pipelines;
      $scope.state.pipelinesLoaded = true;
    });

  });
