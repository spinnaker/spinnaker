'use strict';

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {CANCEL_MODAL_SERVICE} from 'core/cancelModal/cancelModal.service';

let angular = require('angular');

require('./execution.less');

module.exports = angular
  .module('spinnaker.core.delivery.group.executionGroup.execution.directive', [
    require('../../filter/executionFilter.service.js'),
    require('../../filter/executionFilter.model.js'),
    CANCEL_MODAL_SERVICE,
    CONFIRMATION_MODAL_SERVICE,
    require('core/navigation/urlParser.service.js'),
    require('core/scheduler/scheduler.factory'),
  ])
  .directive('execution', function() {
    return {
      restrict: 'E',
      templateUrl: require('./execution.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
        execution: '=',
        standalone: '=',
      },
      controller: 'ExecutionCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ExecutionCtrl', function ($scope, $location, $stateParams, $state, urlParser, schedulerFactory,
                                         settings, ExecutionFilterModel, executionService, cancelModalService,
                                         confirmationModalService) {

    this.pipelinesUrl = [settings.gateUrl, 'pipelines/'].join('/');

    this.showDetails = () => {
      return this.standalone === true || ( this.execution.id === $stateParams.executionId &&
        $state.includes('**.execution.**') );
    };

    this.isActive = (stageIndex) => {
      return this.showDetails() && Number($stateParams.stage) === stageIndex;
    };

    this.toggleDetails = (node) => {
      if (node.index === undefined && $state.current.name.includes('.executions.execution')) {
        $state.go('^');
        return;
      }
      let index = node.index || 0;
      const params = {
        executionId: node.executionId,
        stage: index,
        step: this.execution.stageSummaries[index].firstActiveStage
      };

      if ($state.includes('**.execution', params)) {
        if (!this.standalone) {
          $state.go('^');
        }
      } else {
        if ($state.current.name.includes('.executions.execution') || this.standalone) {
          $state.go('.', params);
        } else {
          $state.go('.execution', params);
        }
      }
    };

    this.getUrl = () => {
      let url = $location.absUrl();
      if (!this.standalone) {
        url = url.replace('/executions', '/executions/details');
      }
      return url;
    };

    let updateViewStateDetails = () => {
      this.viewState.activeStageId = Number($stateParams.stage);
      this.viewState.executionId = $stateParams.executionId;
    };

    $scope.$on('$stateChangeSuccess', updateViewStateDetails);

    this.viewState = {
      activeStageId: Number($stateParams.stage),
      executionId: this.execution.id,
      canTriggerPipelineManually: this.pipelineConfig,
      canConfigure: this.pipelineConfig,
      showPipelineName: ExecutionFilterModel.sortFilter.groupBy !== 'name',
      showStageDuration: ExecutionFilterModel.sortFilter.showStageDuration,
    };

    this.sortFilter = ExecutionFilterModel.sortFilter;

    this.deleteExecution = () => {
      confirmationModalService.confirm({
        header: 'Really delete execution?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the execution history.</p>',
        submitMethod: () => executionService.deleteExecution(this.application, this.execution.id).then( () => {
          if (this.standalone) {
            $state.go('^.^.executions');
          }
        })
      });
    };

    let restartedStage = this.execution.stages.find(stage => stage.context.restartDetails);
    if (restartedStage) {
      this.restartDetails = restartedStage.context.restartDetails;
    } else {
      this.restartDetails = null;
    }

    this.cancelExecution = () => {
      let hasDeployStage = this.execution.stages && this.execution.stages.some(stage => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
      cancelModalService.confirm({
        header: `Really stop execution of ${this.execution.name}?`,
        buttonText: `Stop running ${this.execution.name}`,
        forceable: this.execution.executionEngine === 'v2',
        body: hasDeployStage ? '<b>Note:</b> Any deployments that have begun will continue and need to be cleaned up manually.' : null,
        submitMethod: (reason, force) => executionService.cancelExecution(this.application, this.execution.id, force, reason)
      });
    };

    this.pauseExecution = () => {
      confirmationModalService.confirm({
          header: 'Really pause execution?',
          buttonText: 'Pause',
          body: '<p>This will pause the pipeline for up to 72 hours.</p><p>After 72 hours the pipeline will automatically timeout and fail.</p>',
          submitMethod: () => executionService.pauseExecution(this.application, this.execution.id)
      });
    };

    this.resumeExecution = () => {
      confirmationModalService.confirm({
          header: 'Really resume execution?',
          buttonText: 'Resume',
          submitMethod: () => executionService.resumeExecution(this.application, this.execution.id)
      });
    };

    let activeRefresher = null;

    if (this.execution.isRunning && !this.standalone) {
      activeRefresher = schedulerFactory.createScheduler(1000 * Math.ceil(this.execution.stages.length / 10));
      let refreshing = false;
      activeRefresher.subscribe(() => {
        if (refreshing) {
          return;
        }
        refreshing = true;
        executionService.getExecution(this.execution.id).then(execution => {
          if (!$scope.$$destroyed) {
            executionService.updateExecution(this.application, execution);
          }
          refreshing = false;
        });
      });
      $scope.$on('$destroy', () => activeRefresher.unsubscribe());
    }

    $scope.$on('$destroy', () => {
      if (this.isActive()) {
        this.hideDetails();
      }
    });

  });
