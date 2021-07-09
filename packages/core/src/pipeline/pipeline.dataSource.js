import { module } from 'angular';

import { DELIVERY_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { CLUSTER_SERVICE } from '../cluster/cluster.service';
import { PipelineConfigService } from './config/services/PipelineConfigService';
import { SETTINGS } from '../config/settings';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { EXECUTION_SERVICE } from './service/execution.service';

export const CORE_PIPELINE_PIPELINE_DATASOURCE = 'spinnaker.core.pipeline.dataSource';
export const name = CORE_PIPELINE_PIPELINE_DATASOURCE; // for backwards compatibility
module(CORE_PIPELINE_PIPELINE_DATASOURCE, [EXECUTION_SERVICE, CLUSTER_SERVICE]).run([
  '$q',
  'executionService',
  'clusterService',
  function ($q, executionService, clusterService) {
    const addExecutions = (application, executions) => {
      executionService.transformExecutions(application, executions, application.executions.data);
      return $q.when(executionService.addExecutionsToApplication(application, executions));
    };

    const loadExecutions = (application) => {
      return executionService.getExecutions(application.name, application);
    };

    const loadPipelineConfigs = (application) => {
      const pipelineLoader = PipelineConfigService.getPipelinesForApplication(application.name);
      const strategyLoader = PipelineConfigService.getStrategiesForApplication(application.name);
      return $q
        .all([pipelineLoader, strategyLoader])
        .then(([pipelineConfigs, strategyConfigs]) => ({ pipelineConfigs, strategyConfigs }));
    };

    const addPipelineConfigs = (application, data) => {
      application.strategyConfigs = { data: data.strategyConfigs };
      return $q.when(data.pipelineConfigs);
    };

    const loadRunningExecutions = (application) => {
      return executionService.getRunningExecutions(application.name);
    };

    const addRunningExecutions = (application, data) => {
      executionService.transformExecutions(application, data);
      return $q.when(data);
    };

    const runningExecutionsLoaded = (application) => {
      clusterService.addExecutionsToServerGroups(application);
      executionService.mergeRunningExecutionsIntoExecutions(application);
      application.getDataSource('serverGroups').dataUpdated();
    };

    const executionsLoaded = (application) => {
      addExecutionTags(application);
      executionService.removeCompletedExecutionsFromRunningData(application);
    };

    const addExecutionTags = (application) => {
      EntityTagsReader.addTagsToExecutions(application);
    };

    const addPipelineTags = (application) => {
      EntityTagsReader.addTagsToPipelines(application);
    };

    if (SETTINGS.feature.pipelines !== false) {
      ApplicationDataSourceRegistry.registerDataSource({
        optional: true,
        primary: true,
        icon: 'fa fa-xs fa-fw fa-list',
        iconName: 'spMenuPipelines',
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
        defaultData: [],
      });

      ApplicationDataSourceRegistry.registerDataSource({
        key: 'pipelineConfigs',
        loader: loadPipelineConfigs,
        onLoad: addPipelineConfigs,
        afterLoad: addPipelineTags,
        lazy: true,
        visible: false,
        defaultData: [],
      });

      ApplicationDataSourceRegistry.registerDataSource({
        key: 'runningExecutions',
        visible: false,
        loader: loadRunningExecutions,
        onLoad: addRunningExecutions,
        afterLoad: runningExecutionsLoaded,
        defaultData: [],
      });
    }
  },
]);
