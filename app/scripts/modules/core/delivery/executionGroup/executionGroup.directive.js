'use strict';

let angular = require('angular');

require('./executionGroup.less');

module.exports = angular
  .module('spinnaker.core.delivery.group.executionGroup.directive', [
    require('../filter/executionFilter.service.js'),
    require('../filter/executionFilter.model.js'),
    require('../triggers/triggersTag.directive.js'),
    require('../triggers/nextRun.component'),
    require('./execution/execution.directive.js'),
  ])
  .directive('executionGroup', function() {
    return {
      restrict: 'E',
      templateUrl: require('./executionGroup.directive.html'),
      scope: {},
      bindToController: {
        group: '=',
        application: '=',
      },
      controller: 'executionGroupCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('executionGroupCtrl', function($scope, $timeout, _, $state, settings, $stateParams, $uibModal, executionService, collapsibleSectionStateCache,
                                               ExecutionFilterModel, pipelineConfigService) {
    this.showDetails = function(executionId) {
      return executionId === $stateParams.executionId &&
        $state.includes('**.execution.**');
    };

    this.isShowingDetails = () => this.group.executions
      .map((execution) => execution.id)
      .some(this.showDetails);

    this.configure = (id) => {
      if ($state.current.name.indexOf('.executions.execution') === -1) {
        $state.go('^.pipelineConfig', { pipelineId: id });
      } else {
        $state.go('^.^.pipelineConfig', { pipelineId: id });
      }
    };

    this.hideDetails = () => $state.go('.^');


    let getSectionCacheKey = () => {
      return executionService.getSectionCacheKey(ExecutionFilterModel.sortFilter.groupBy, this.application.name, this.group.heading);
    };

    this.pipelineConfig = _.find(this.application.pipelineConfigs.data, { name: this.group.heading });

    this.viewState = {
      triggeringExecution: false,
      open: this.isShowingDetails() || !collapsibleSectionStateCache.isSet(getSectionCacheKey()) || collapsibleSectionStateCache.isExpanded(getSectionCacheKey()),
      poll: null,
      canTriggerPipelineManually: this.pipelineConfig,
      canConfigure: this.pipelineConfig,
      showPipelineName: ExecutionFilterModel.sortFilter.groupBy !== 'name',
    };

    $scope.$on('$destroy', () => {
      if (this.viewState.poll) {
        $timeout.cancel(this.viewState.poll);
      }
    });

    this.toggle = () => {
      this.viewState.open = !this.viewState.open;
      if (this.isShowingDetails()) {
        this.hideDetails();
      }
      collapsibleSectionStateCache.setExpanded(getSectionCacheKey(), this.viewState.open);
    };

    let startPipeline = (command) => {
      this.viewState.triggeringExecution = true;
      return pipelineConfigService.triggerPipeline(this.application.name, command.pipelineName, command.trigger).then(
        (result) => {
          var newPipelineId = result.ref.split('/').pop();
          var monitor = executionService.waitUntilNewTriggeredPipelineAppears(this.application, command.pipelineName, newPipelineId);
          monitor.then(() => {
            this.viewState.triggeringExecution = false;
          });
          this.viewState.poll = monitor;
        },
        () => {
          this.viewState.triggeringExecution = false;
        });
    };

    this.triggerPipeline = () => {
      $uibModal.open({
        templateUrl: require('../manualExecution/manualPipelineExecution.html'),
        controller: 'ManualPipelineExecutionCtrl as vm',
        resolve: {
          pipeline: () => this.pipelineConfig,
          application: () => this.application,
          currentlyRunningExecutions: () => this.group.runningExecutions,
        }
      }).result.then(startPipeline);
    };

    $scope.$on('toggle-expansion', (event, expanded) => {
      if (this.viewState.open !== expanded) {
        this.toggle();
      }
    });

    $scope.$on('$stateChangeSuccess', () => {
      // If the heading is collapsed, but we've clicked on a link to an execution in this group, expand the group
      if (this.isShowingDetails() && !this.viewState.open) {
        this.toggle();
      }
    });
  });
