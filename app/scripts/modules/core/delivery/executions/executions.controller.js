'use strict';

let angular = require('angular');

import {EXECUTION_FILTER_MODEL} from 'core/delivery/filter/executionFilter.model';
import {EXECUTION_FILTER_SERVICE} from 'core/delivery/filter/executionFilter.service';
import {EXECUTION_SERVICE} from '../service/execution.service';
import {INSIGHT_NGMODULE} from 'core/insight/insight.module';
import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';
import {SCROLL_TO_SERVICE} from '../../utils/scrollTo/scrollTo.service';

module.exports = angular.module('spinnaker.core.delivery.executions.controller', [
  EXECUTION_SERVICE,
  PIPELINE_CONFIG_SERVICE,
  SCROLL_TO_SERVICE,
  INSIGHT_NGMODULE.name,
  EXECUTION_FILTER_MODEL,
  EXECUTION_FILTER_SERVICE,
  require('../create/create.module.js'),
])
  .controller('ExecutionsCtrl', function($scope, $state, $q, $uibModal, $stateParams,
                                         pipelineConfigService, scrollToService, $timeout,
                                         executionService, executionFilterModel, executionFilterService,
                                         InsightFilterStateModel) {

    if (executionFilterModel.mostRecentApplication !== $scope.application.name) {
      executionFilterModel.groups = [];
      executionFilterModel.mostRecentApplication = $scope.application.name;
    }

    let scrollIntoView = (delay = 200) => scrollToService.scrollTo('#execution-' + $stateParams.executionId, '.all-execution-groups', 225, delay);

    let application = $scope.application;
    this.application = application;
    if ($scope.application.notFound) {
      return;
    }

    application.activeState = application.executions;
    $scope.$on('$destroy', () => {
      application.activeState = application;
      application.executions.deactivate();
      application.pipelineConfigs.deactivate();
    });

    this.InsightFilterStateModel = InsightFilterStateModel;

    this.filter = executionFilterModel.sortFilter;

    this.clearFilters = () => {
      executionFilterService.clearFilters();
      this.updateExecutionGroups(true);
    };

    this.updateExecutionGroups = (reload) => {
      normalizeExecutionNames();
      executionFilterModel.applyParamsToUrl();
      if (reload) {
        this.application.executions.refresh(true);
        this.application.executions.reloadingForFilters = true;
      } else {
        executionFilterService.updateExecutionGroups(this.application);
        this.tags = executionFilterModel.tags;
        // updateExecutionGroups is debounced by 25ms, so we need to delay setting the loading flag a bit
        $timeout(() => { this.viewState.loading = false; }, 50);
      }
    };

    this.viewState = {
      loading: true,
      triggeringExecution: false,
    };

    application.executions.activate();
    application.pipelineConfigs.activate();

    application.executions.onRefresh($scope, () => {
      // if an execution was selected but is no longer present, navigate up
      if ($state.params.executionId) {
        if (application.getDataSource('executions').data.every(e => e.id !== $state.params.executionId)) {
          $state.go('.^');
        }
      }
    });

    $q.all([application.executions.ready(), application.pipelineConfigs.ready()]).then(() => {
      this.updateExecutionGroups();
      if ($stateParams.executionId) {
        scrollIntoView();
      }
    });

    $scope.filterCountOptions = [1, 2, 5, 10, 20, 30, 40, 50];

    let dataInitializationFailure = () => {
      this.viewState.loading = false;
      this.viewState.initializationError = true;
    };

    function normalizeExecutionNames() {
      if (application.executions.loadFailure) {
        dataInitializationFailure();
      }
      let executions = application.executions.data || [];
      var configurations = application.pipelineConfigs.data || [];
      executions.forEach(function(execution) {
        if (execution.pipelineConfigId) {
          var configMatches = configurations.filter(function(configuration) {
            return configuration.id === execution.pipelineConfigId;
          });
          if (configMatches.length) {
            execution.name = configMatches[0].name;
          }
        }
      });
    }

    this.application.executions.onRefresh($scope, normalizeExecutionNames, dataInitializationFailure);

    this.toggleExpansion = (expand) => {
      $scope.$broadcast('toggle-expansion', expand);
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
          pipeline: () => null,
          application: () => this.application,
        }
      }).result.then(startPipeline);
    };

    $scope.$on('$stateChangeSuccess', (event, toState, toParams, fromState, fromParams) => {
      // if we're navigating to a different execution on the same page, scroll the new execution into view
      // or, if we are navigating back to the same execution after scrolling down the page, scroll it into view
      // but don't scroll it into view if we're navigating to a different stage in the same execution
      let shouldScroll = false;
      if (toState.name.indexOf(fromState.name) === 0 && toParams.application === fromParams.application && toParams.executionId) {
        shouldScroll = true;
        if (toParams.executionId === fromParams.executionId && toParams.details) {
          if (toParams.stage !== fromParams.stage || toParams.step !== fromParams.step || toParams.details !== fromParams.details) {
            shouldScroll = false;
          }
        }
      }
      if (shouldScroll) {
        scrollIntoView(0);
      }
    });
  });
