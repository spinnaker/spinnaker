'use strict';

angular.module('deckApp.delivery.executionGroupHeading.controller', [
  'deckApp.utils.lodash',
  'deckApp.pipelines.config.service',
  'deckApp.delivery.executions.service'
])
  .controller('executionGroupHeading', function($scope, $timeout, pipelineConfigService, executionsService, _) {
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
      pipelineConfigService.triggerPipeline($scope.application.name, $scope.value).then(
        function() {
          var monitor = executionsService.waitUntilNewTriggeredPipelineAppears($scope.application, $scope.value, ignoreList);
          monitor.then(function() {
            $scope.viewState.triggeringExecution = false;
          });
          $scope.viewState.poll = monitor;
        },
        function() {
          $scope.viewState.triggeringExecution = false;
        });
    };
  });

