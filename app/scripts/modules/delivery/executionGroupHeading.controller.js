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

    var configuration = _.find($scope.configurations, { name: $scope.value });

    function updateTriggerInfo() {

      if (configuration) {

        controller.triggerCount = configuration.triggers ? configuration.triggers.length : 0;
        controller.activeTriggerCount = _.filter(configuration.triggers, { enabled: true }).length;
        if (controller.activeTriggerCount) {
          if (controller.activeTriggerCount > 1) {
            controller.triggerTooltip = 'This pipeline has ' + controller.activeTriggerCount + ' active triggers.<br/> <b>Click to disable</b>';
          } else {
            controller.triggerTooltip = 'This pipeline has an active trigger.<br/> <b>Click to disable</b>';
          }
        } else {
          if (controller.triggerCount) {
            if (controller.triggerCount > 1) {
              controller.triggerTooltip = 'This pipeline has multiple triggers, but they are all currently disabled.<br/> <b>Click to enable all triggers</b>';
            } else {
              controller.triggerTooltip = 'This pipeline has a trigger, but it is currently disabled.<br/> <b>Click to enable.</b>';
            }
          }
        }
      }
    }

    controller.toggleTriggers = function() {
      pipelineConfigService.toggleTriggers(configuration).then(updateTriggerInfo);
    };

    controller.canTriggerPipelineManually = $scope.filter.execution.groupBy === 'name' && configuration;

    function startPipeline(trigger) {
      $scope.viewState.triggeringExecution = true;
      return pipelineConfigService.triggerPipeline($scope.application.name, $scope.value, trigger).then(
        function (result) {
          var newPipelineId = result.ref.split('/').pop();
          var monitor = executionsService.waitUntilNewTriggeredPipelineAppears($scope.application, $scope.value, newPipelineId);
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

    controller.getCurrentlyRunningExecutions = getCurrentlyRunningExecutions;

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

    updateTriggerInfo();
  });

