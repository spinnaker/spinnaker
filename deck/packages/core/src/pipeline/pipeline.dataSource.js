import { DELIVERY_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { PipelineConfigService } from './config/services/PipelineConfigService';
import { SETTINGS } from '../config/settings';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';

export function registerPipelineDataSources($q, executionService, clusterService) {
  const registerOnce = (config) => {
    if (!ApplicationDataSourceRegistry.getDataSources().some(({ key }) => key === config.key)) {
      ApplicationDataSourceRegistry.registerDataSource(config);
    }
  };

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
    executionService.mergeRunningExecutionsIntoExecutions(application);

    const serverGroups = application.getDataSource('serverGroups');
    if (!serverGroups) {
      return;
    }

    clusterService.addExecutionsToServerGroups(application);
    serverGroups.dataUpdated();
  };

  const executionsLoaded = (application) => {
    executionService.mergeRunningExecutionsIntoExecutions(application);
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
    registerOnce({
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

    registerOnce({
      key: 'pipelineConfigs',
      loader: loadPipelineConfigs,
      onLoad: addPipelineConfigs,
      afterLoad: addPipelineTags,
      lazy: true,
      visible: false,
      defaultData: [],
    });

    registerOnce({
      key: 'runningExecutions',
      visible: false,
      loader: loadRunningExecutions,
      onLoad: addRunningExecutions,
      afterLoad: runningExecutionsLoaded,
      defaultData: [],
    });
  }
}
