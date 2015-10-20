'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionGroupHeading.controller', [
  require('../utils/lodash.js'),
  require('../pipeline/config/services/pipelineConfigService.js'),
  require('./execution.service.js'),
  require('../cache/collapsibleSectionStateCache.js'),
  require('./manualPipelineExecution.controller.js'),
  require('../confirmationModal/confirmationModal.service.js'),
])
  .controller('executionGroupHeading', function($scope, $uibModal, $timeout, pipelineConfigService, executionService, collapsibleSectionStateCache, _) {
    var controller = this;

    function getSectionCacheKey() {
      return executionService.getSectionCacheKey($scope.filter.execution.groupBy, $scope.application.name, $scope.value);
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

    $scope.configuration = configuration;

    controller.canTriggerPipelineManually = $scope.filter.execution.groupBy === 'name' && configuration;
    controller.isRetired = $scope.filter.execution.groupBy === 'name' && !configuration;

    function startPipeline(trigger) {
      $scope.viewState.triggeringExecution = true;
      return pipelineConfigService.triggerPipeline($scope.application.name, $scope.value, trigger).then(
        function (result) {
          var newPipelineId = result.ref.split('/').pop();
          var monitor = executionService.waitUntilNewTriggeredPipelineAppears($scope.application, $scope.value, newPipelineId);
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
        return execution.isActive;
      });
    }

    controller.getCurrentlyRunningExecutions = getCurrentlyRunningExecutions;

    controller.triggerPipeline = function() {
      var pipeline = _.find($scope.configurations, {name: $scope.value});
      var currentlyRunningExecutions = getCurrentlyRunningExecutions();

      $uibModal.open({
        templateUrl: require('./manualPipelineExecution.html'),
        controller: 'ManualPipelineExecutionCtrl as ctrl',
        resolve: {
          pipeline: function () { return pipeline; },
          currentlyRunningExecutions: function() { return currentlyRunningExecutions; },
        }
      }).result.then(startPipeline);
    };

    $scope.$on('toggle-expansion', (event, expanded) => {
      if ($scope.viewState.open !== expanded) {
        controller.toggle();
      }
    });
  })
  .name;

