'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executions.controller', [
  require('../service/execution.service.js'),
  require('../../pipeline/config/services/pipelineConfigService.js'),
  require('../../utils/scrollTo/scrollTo.service.js'),
  require('../../insight/insightFilterState.model.js'),
  require('../filter/executionFilter.model.js'),
  require('../filter/executionFilter.service.js'),
  require('../create/create.module.js'),
])
  .controller('ExecutionsCtrl', function($scope, $state, $q, $uibModal, $stateParams,
                                         pipelineConfigService, scrollToService,
                                         executionService, ExecutionFilterModel, executionFilterService,
                                         InsightFilterStateModel) {

    if (ExecutionFilterModel.mostRecentApplication !== $scope.application.name) {
      ExecutionFilterModel.groups = [];
      ExecutionFilterModel.mostRecentApplication = $scope.application.name;
    }

    let scrollIntoView = () => scrollToService.scrollTo('execution-' + $stateParams.executionId, '.all-execution-groups', 280, 300);

    let application = $scope.application;
    this.application = application;

    this.InsightFilterStateModel = InsightFilterStateModel;

    this.filter = ExecutionFilterModel.sortFilter;

    this.clearFilters = () => {
      executionFilterService.clearFilters();
      this.updateExecutionGroups();
    };

    this.updateExecutionGroups = () => {
      normalizeExecutionNames();
      ExecutionFilterModel.applyParamsToUrl();
      executionFilterService.updateExecutionGroups(this.application);
      this.tags = ExecutionFilterModel.tags;
      this.viewState.loading = false;
    };

    this.viewState = {
      loading: true,
      triggeringExecution: false,
    };

    let executionLoader = $q.when(null);
    if (!application.executionsLoaded) {
      let deferred = $q.defer();
      executionLoader = deferred.promise;
      $scope.$on('executions-loaded', deferred.resolve);
    }

    let configLoader = $q.when(null);
    if (application.pipelineConfigsLoading) {
      let deferred = $q.defer();
      configLoader = deferred.promise;
      $scope.$on('pipelineConfigs-loaded', deferred.resolve);
    }

    $q.all([executionLoader, configLoader]).then(() => {
      this.updateExecutionGroups();
      this.viewState.loading = false;
      if ($stateParams.executionId) {
        scrollIntoView();
      }
    });

    $scope.filterCountOptions = [1, 2, 5];

    $scope.$watch(() => ExecutionFilterModel.groups, () => {

    });

    function normalizeExecutionNames() {
      let executions = application.executions || [];
      var configurations = application.pipelineConfigs || [];
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

    let dataInitializationFailure = () => {
      this.viewState.loading = false;
      this.viewState.initializationError = true;
    };

    $scope.$on('executions-load-failure', dataInitializationFailure);
    $scope.$on('executions-reloaded', normalizeExecutionNames);

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
        controller: 'ManualPipelineExecutionCtrl as ctrl',
        resolve: {
          pipeline: () => null,
          application: () => this.application,
        }
      }).result.then(startPipeline);
    };



  }).name;
