'use strict';

angular.module('deckApp.delivery')
  .controller('executionGroupHeading', function($scope, pipelineConfigService, $stateParams, executionsService, _, $timeout) {
    var controller = this;

    $scope.viewState = {
      triggeringExecution: false,
      open: true,
      poll: null
    };

    $scope.$on('$destroy', function() {
      if ($scope.viewState.poll) {
        $timeout.cancel($scope.viewState.poll);
      }
    });

    controller.toggle = function() {
      $scope.viewState.open = !$scope.viewState.open;
    };

    controller.canTriggerPipelineManually = function() {
      return $scope.filter.execution.groupBy === 'name' && _.find($scope.configurations, { name: $scope.value });
    };

    controller.triggerPipeline = function() {
      $scope.viewState.triggeringExecution = true;
      var toIgnore = ($scope.executions || []).filter(function(execution) {
        return execution.status === 'RUNNING' || execution.status === 'NOT_STARTED';
      });
      var ignoreList = _.pluck(toIgnore, 'id');
      pipelineConfigService.triggerPipeline($stateParams.application, $scope.value).then(
        function() {
          var monitor = executionsService.waitUntilNewTriggeredPipelineAppears($scope.value, ignoreList);
          monitor.then(function() {
            executionsService.forceRefresh();
            $scope.viewState.triggeringExecution = false;
          });
          $scope.viewState.poll = monitor;
        },
        function() {
          $scope.viewState.triggeringExecution = false;
        });
    };
  });

