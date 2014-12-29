'use strict';

angular.module('deckApp.delivery')
  .controller('executionGroupHeading', function($scope, pipelineConfigService, $stateParams, executionsService, _) {
    var controller = this;

    $scope.viewState = {
      triggeringExecution: false,
      open: true
    };

    controller.toggle = function() {
      $scope.viewState.open = !$scope.viewState.open;
    };

    controller.canTriggerPipelineManually = function() {
      return $scope.filter.execution.groupBy === 'name' && _.find($scope.configurations, { name: $scope.value });
    };

    controller.triggerPipeline = function() {
      $scope.viewState.triggeringExecution = true;
      pipelineConfigService.triggerPipeline($stateParams.application, $scope.value).then(
        function() {
          executionsService.forceRefresh();
          $scope.viewState.triggeringExecution = false;
        },
        function() {
          $scope.viewState.triggeringExecution = false;
        });
    };
  });

