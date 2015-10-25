'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.filter.executionFilter.controller', [
  require('./executionFilter.service.js'),
  require('./executionFilter.model.js'),
  require('../../utils/lodash.js'),
])
  .controller('ExecutionFilterCtrl', function ($scope, $rootScope, _, $q, pipelineConfigService,
                                               executionFilterService, ExecutionFilterModel) {

    $scope.sortFilter = ExecutionFilterModel.sortFilter;

    this.viewState = {
      pipelineReorderDisabled: true,
    };

    this.enablePipelineReorder = () => {
      this.viewState.pipelineReorderDisabled = false;
      this.pipelineSortOptions.disabled = false;
    };

    this.disablePipelineReorder = () => {
      this.viewState.pipelineReorderDisabled = true;
      this.pipelineSortOptions.disabled = true;
    };

    this.updateExecutionGroups = () => {
      ExecutionFilterModel.applyParamsToUrl();
      executionFilterService.updateExecutionGroups(this.application);
    };

    this.clearFilters = () => {
      executionFilterService.clearFilters();
      executionFilterService.updateExecutionGroups(this.application);
    };

    this.initialize = () => {
      let allOptions = _.sortBy(this.application.pipelineConfigs, 'index')
        .concat(this.application.executions)
        .filter((option) => option && option.name)
        .map((option) => option.name);
      this.pipelineNames = _.uniq(allOptions);
      this.updateExecutionGroups();
    };

    $scope.$on('executions-reloaded', this.initialize);
    $scope.$on('pipelineConfigs-reloaded', this.initialize);
    this.initialize();

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      ExecutionFilterModel.activate();
      executionFilterService.updateExecutionGroups(this.application);
    }));

    let updatePipelines = (pipelines) => {
      $q.all(pipelines.map(function(pipeline) {
        return pipelineConfigService.savePipeline(pipeline);
      }));
    };

    this.pipelineSortOptions = {
      axis: 'y',
      delay: 150,
      disabled: true,
      stop: () => {
        var dirty = [];
        this.application.pipelineConfigs.forEach((pipeline, index) => {
          if (pipeline.index !== index) {
            pipeline.index = index;
            dirty.push(pipeline);
          }
        });
        updatePipelines(dirty);
        this.initialize();
      }
    };

  }
)
.name;
