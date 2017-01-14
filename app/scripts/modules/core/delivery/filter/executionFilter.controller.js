'use strict';

import _ from 'lodash';

let angular = require('angular');

import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.delivery.filter.executionFilter.controller', [
  require('./executionFilter.service.js'),
  require('./executionFilter.model.js'),
  require('angulartics'),
  PIPELINE_CONFIG_SERVICE
])
  .controller('ExecutionFilterCtrl', function ($scope, $rootScope, $q, pipelineConfigService,
                                               executionFilterService, ExecutionFilterModel, $analytics) {

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

    this.updateExecutionGroups = (reload) => {
      ExecutionFilterModel.applyParamsToUrl();
      if (reload) {
        this.application.executions.reloadingForFilters = true;
        this.application.executions.refresh();
      } else {
        executionFilterService.updateExecutionGroups(this.application);
      }
    };

    this.clearFilters = () => {
      executionFilterService.clearFilters();
      executionFilterService.updateExecutionGroups(this.application);
    };

    this.initialize = () => {
      if (this.application.pipelineConfigs.loadFailure) {
        return;
      }
      let configs = (this.application.pipelineConfigs.data || []).concat(this.application.strategyConfigs.data || []);
      let allOptions = _.orderBy(configs, ['strategy', 'index'], ['desc', 'asc'])
        .concat(this.application.executions.data)
        .filter((option) => option && option.name)
        .map((option) => option.name);
      this.pipelineNames = _.uniq(allOptions);
      this.updateExecutionGroups();
      this.application.executions.reloadingForFilters = false;
    };

    this.application.executions.onRefresh($scope, this.initialize);
    this.application.pipelineConfigs.onRefresh($scope, this.initialize);

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
        $analytics.eventTrack('Reordered pipeline', {category: 'Pipelines'});
        var dirty = [];
        this.application.pipelineConfigs.data.forEach((pipeline, index) => {
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
);
