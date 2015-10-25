'use strict';

let angular = require('angular');

require('./executionGroup.less');

module.exports = angular
  .module('spinnaker.core.delivery.group.executionGroup.directive', [
    require('../filter/executionFilter.service.js'),
    require('../filter/executionFilter.model.js'),
    require('../../confirmationModal/confirmationModal.service.js'),
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
                                               ExecutionFilterModel, pipelineConfigService, confirmationModalService) {
    // TODO: MOVE TO SEPARATE DIRECTIVE
    this.showDetails = function(executionId) {
      return executionId === $stateParams.executionId &&
        $state.includes('**.execution.**');
    };

    this.isActive = (executionId, stageIndex) => {
      return this.showDetails(executionId) && Number($stateParams.stage) === stageIndex;
    };

    let updateViewStateDetails = () => {
      this.viewState.activeStageId = Number($stateParams.stage);
      this.viewState.executionId = $stateParams.executionId;
    };

    this.pipelinesUrl = [settings.gateUrl, 'pipelines/'].join('/');


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

    this.pipelineConfig = _.find(this.application.pipelineConfigs, { name: this.group.heading });

    this.viewState = {
      triggeringExecution: false,
      open: !collapsibleSectionStateCache.isSet(getSectionCacheKey()) || collapsibleSectionStateCache.isExpanded(getSectionCacheKey()),
      poll: null,
      activeStageId: Number($stateParams.stage),
      executionId: $stateParams.executionId,
      canTriggerPipelineManually: this.pipelineConfig,
      canConfigure: this.pipelineConfig,
      isRetired: ExecutionFilterModel.sortFilter.groupBy === 'name' && !this.pipelineConfig,
      showPipelineName: ExecutionFilterModel.sortFilter.groupBy !== 'name',
    };

    $scope.$on('$destroy', () => {
      if (this.viewState.poll) {
        $timeout.cancel(this.viewState.poll);
      }
    });

    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      $scope.detailsTarget = toParams.executionId;
      updateViewStateDetails();
    });

    this.toggleDetails = (node) => {
      const params = { executionId: node.executionId, stage: node.index};
      if ($state.includes('**.execution', params)) {
        $state.go('^');
      } else {
        if ($state.current.name.indexOf('.executions.execution') !== -1) {
          $state.go('.', params);
        } else {
          $state.go('.execution', params);
        }
      }
    };

    this.toggle = () => {
      this.viewState.open = !this.viewState.open;
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
        controller: 'ManualPipelineExecutionCtrl as ctrl',
        resolve: {
          pipeline: () => this.pipelineConfig,
          application: () => this.application,
          currentlyRunningExecutions: () => this.group.runningExecutions,
        }
      }).result.then(startPipeline);
    };

    this.deleteExecution = (execution) => {
      confirmationModalService.confirm({
        header: 'Really delete execution?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the execution history.</p>',
        submitMethod: () => executionService.deleteExecution(this.application, execution.id)
      });
    };

    this.cancelExecution = (execution) => {
      confirmationModalService.confirm({
        header: 'Really stop execution of ' + execution.name + '?',
        buttonText: 'Stop running ' + execution.name,
        destructive: false,
        submitMethod: () => executionService.cancelExecution(execution.id)
      });
    };

    $scope.$on('toggle-expansion', (event, expanded) => {
      if (this.viewState.open !== expanded) {
        this.toggle();
      }
    });
  }).name;
