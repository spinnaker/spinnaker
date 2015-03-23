'use strict';

angular.module('deckApp.delivery.executionGroupHeading.controller', [
  'deckApp.utils.lodash',
  'deckApp.pipelines.config.service',
  'deckApp.delivery.executions.service',
  'deckApp.caches.collapsibleSectionState',
  'deckApp.delivery.manualPipelineExecution.controller',
  'deckApp.confirmationModal.service'
])
  .controller('executionGroupHeading', function($scope, $modal, $timeout, pipelineConfigService, executionsService, collapsibleSectionStateCache, _) {
    var controller = this;

    function getSectionCacheKey() {
      return executionsService.getSectionCacheKey($scope.filter.execution.groupBy, $scope.application.name, $scope.value);
    }

    $scope.viewState = {
      triggeringExecution: false,
      open: !collapsibleSectionStateCache.isSet(getSectionCacheKey()) || collapsibleSectionStateCache.isExpanded(getSectionCacheKey()),
      poll: null
    };

    $scope.$on('$destroy', function() {
      if ($scope.viewState.poll) {
        $timeout.cancel($scope.viewState.poll);
      }
    });

    controller.toggle = function() {
      $scope.viewState.open = !$scope.viewState.open;
      collapsibleSectionStateCache.setExpanded(getSectionCacheKey(), $scope.viewState.open);
    };

    controller.canTriggerPipelineManually = function() {
      return $scope.filter.execution.groupBy === 'name' && _.find($scope.configurations, { name: $scope.value });
    };


    function startPipeline(trigger) {
      $scope.viewState.triggeringExecution = true;
      var ignoreList = _.pluck(getCurrentlyRunningExecutions(), 'id');
      return pipelineConfigService.triggerPipeline($scope.application.name, $scope.value, trigger).then(
        function () {
          var monitor = executionsService.waitUntilNewTriggeredPipelineAppears($scope.application, $scope.value, ignoreList);
          monitor.then(function () {
            $scope.viewState.triggeringExecution = false;
          });
          $scope.viewState.poll = monitor;
        },
        function () {
          $scope.viewState.triggeringExecution = false;
        });
    }

    function getCurrentlyRunningExecutions() {
      return ($scope.executions || []).filter(function (execution) {
        if (execution.name !== $scope.value) {
          return false;
        }
        return execution.status === 'RUNNING' || execution.status === 'NOT_STARTED';
      });
    }

    controller.triggerPipeline = function() {
      var pipeline = _.find($scope.configurations, {name: $scope.value});
      var currentlyRunningExecutions = getCurrentlyRunningExecutions();

      $modal.open({
        templateUrl: 'scripts/modules/delivery/manualPipelineExecution.html',
        controller: 'ManualPipelineExecutionCtrl as ctrl',
        resolve: {
          pipeline: function () { return pipeline; },
          currentlyRunningExecutions: function() { return currentlyRunningExecutions; },
        }
      }).result.then(startPipeline);
    };
  });

