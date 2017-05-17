'use strict';

import _ from 'lodash';

const angular = require('angular');

import {EXECUTION_FILTER_MODEL} from 'core/delivery/filter/executionFilter.model';
import {EXECUTION_FILTER_SERVICE} from 'core/delivery/filter/executionFilter.service';
import {PIPELINE_CONFIG_SERVICE} from 'core/pipeline/config/services/pipelineConfig.service';

module.exports = angular.module('spinnaker.core.delivery.filter.executionFilter.controller', [
  EXECUTION_FILTER_SERVICE,
  EXECUTION_FILTER_MODEL,
  require('angulartics'),
  PIPELINE_CONFIG_SERVICE
])
  .controller('ExecutionFilterCtrl', function ($scope, $rootScope, $q, pipelineConfigService,
                                               executionFilterService, executionFilterModel, $analytics) {
    this.tags = executionFilterModel.tags;
    $scope.sortFilter = executionFilterModel.sortFilter;

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
      executionFilterModel.applyParamsToUrl();
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
      this.groupsUpdatedSubscription = executionFilterService.groupsUpdatedStream.subscribe(() => this.tags = executionFilterModel.tags);
    };

    this.application.executions.onRefresh($scope, this.initialize);
    this.application.pipelineConfigs.onRefresh($scope, this.initialize);

    this.initialize();

    this.locationChangeUnsubscribe = $rootScope.$on('$locationChangeSuccess', () => {
      executionFilterModel.activate();
      executionFilterService.updateExecutionGroups(this.application);
    });

    $scope.$on('$destroy', () => {
      this.groupsUpdatedSubscription.unsubscribe();
      this.locationChangeUnsubscribe();
    });

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
        const dirty = [];
        this.application.pipelineConfigs.data.concat(this.application.strategyConfigs.data).forEach((pipeline) => {
          const newIndex = this.pipelineNames.indexOf(pipeline.name);
          if (pipeline.index !== newIndex) {
            pipeline.index = newIndex;
            dirty.push(pipeline);
          }
        });
        updatePipelines(dirty);
        this.initialize();
      }
    };

  }
);
