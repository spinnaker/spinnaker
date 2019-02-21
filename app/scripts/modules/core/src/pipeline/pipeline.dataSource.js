const angular = require('angular');

import { ApplicationDataSourceRegistry } from 'core/application/service/ApplicationDataSourceRegistry';
import { DELIVERY_KEY } from 'core/application/nav/defaultCategories';
import { EntityTagsReader } from 'core/entityTag/EntityTagsReader';
import { EXECUTION_SERVICE } from './service/execution.service';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { SETTINGS } from 'core/config/settings';
import { CLUSTER_SERVICE } from 'core/cluster/cluster.service';

module.exports = angular.module('spinnaker.core.pipeline.dataSource', [EXECUTION_SERVICE, CLUSTER_SERVICE]).run([
  '$q',
  'executionService',
  'clusterService',
  function($q, executionService, clusterService) {
    let addExecutions = (application, executions) => {
      executionService.transformExecutions(application, executions, application.executions.data);
      return $q.when(executionService.addExecutionsToApplication(application, executions));
    };

    let loadExecutions = application => {
      return executionService.getExecutions(application.name, application);
    };

    let loadPipelineConfigs = application => {
      let pipelineLoader = PipelineConfigService.getPipelinesForApplication(application.name),
        strategyLoader = PipelineConfigService.getStrategiesForApplication(application.name);
      return $q.all({ pipelineConfigs: pipelineLoader, strategyConfigs: strategyLoader });
    };

    let addPipelineConfigs = (application, data) => {
      application.strategyConfigs = { data: data.strategyConfigs };
      return $q.when(data.pipelineConfigs);
    };

    let loadRunningExecutions = application => {
      return executionService.getRunningExecutions(application.name);
    };

    let addRunningExecutions = (application, data) => {
      executionService.transformExecutions(application, data);
      return $q.when(data);
    };

    let runningExecutionsLoaded = application => {
      clusterService.addExecutionsToServerGroups(application);
      executionService.mergeRunningExecutionsIntoExecutions(application);
      application.getDataSource('serverGroups').dataUpdated();
    };

    let executionsLoaded = application => {
      addExecutionTags(application);
      executionService.removeCompletedExecutionsFromRunningData(application);
    };

    let addExecutionTags = application => {
      EntityTagsReader.addTagsToExecutions(application);
    };

    let addPipelineTags = application => {
      EntityTagsReader.addTagsToPipelines(application);
    };

    if (SETTINGS.feature.pipelines !== false) {
      ApplicationDataSourceRegistry.registerDataSource({
        optional: true,
        primary: true,
        icon: 'fa fa-xs fa-fw fa-list',
        key: 'executions',
        label: 'Pipelines',
        category: DELIVERY_KEY,
        sref: '.pipelines.executions',
        activeState: '**.pipelines.**',
        loader: loadExecutions,
        onLoad: addExecutions,
        afterLoad: executionsLoaded,
        lazy: true,
        badge: 'runningExecutions',
        description: 'Orchestrated deployment management',
      });

      ApplicationDataSourceRegistry.registerDataSource({
        key: 'pipelineConfigs',
        loader: loadPipelineConfigs,
        onLoad: addPipelineConfigs,
        afterLoad: addPipelineTags,
        lazy: true,
        visible: false,
      });

      ApplicationDataSourceRegistry.registerDataSource({
        key: 'runningExecutions',
        visible: false,
        loader: loadRunningExecutions,
        onLoad: addRunningExecutions,
        afterLoad: runningExecutionsLoaded,
      });
    }
  },
]);
