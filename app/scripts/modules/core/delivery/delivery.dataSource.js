import {DataSourceConfig} from '../application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.dataSource', [
    dataSourceRegistryModule,
    require('./service/execution.service'),
    require('../pipeline/config/services/pipelineConfigService'),
    require('../cluster/cluster.service'),
    require('../config/settings'),
  ])
  .run(function($q, applicationDataSourceRegistry, executionService, pipelineConfigService, clusterService, settings) {

    let addExecutions = (application, executions) => {
      executionService.transformExecutions(application, executions);
      return $q.when(executionService.addExecutionsToApplication(application, executions));
    };

    let loadExecutions = (application) => {
      return executionService.getExecutions(application.name);
    };

    let loadPipelineConfigs = (application) => {
      let pipelineLoader = pipelineConfigService.getPipelinesForApplication(application.name),
          strategyLoader = pipelineConfigService.getStrategiesForApplication(application.name);
      return $q.all({pipelineConfigs: pipelineLoader, strategyConfigs: strategyLoader});
    };

    let addPipelineConfigs = (application, data) => {
      application.strategyConfigs = { data: data.strategyConfigs };
      return $q.when(data.pipelineConfigs);
    };

    let loadRunningExecutions = (application) => {
      return executionService.getRunningExecutions(application.name);
    };

    let addRunningExecutions = (application, data) => {
      executionService.transformExecutions(application, data);
      clusterService.addExecutionsToServerGroups(application);
      return $q.when(data);
    };


    if (settings.feature && settings.feature.pipelines !== false) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        optional: true,
        key: 'executions',
        label: 'Pipelines',
        sref: '.pipelines.executions',
        activeState: '**.pipelines.**',
        loader: loadExecutions,
        onLoad: addExecutions,
        lazy: true,
        badge: 'runningExecutions',
      }));

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'pipelineConfigs',
        loader: loadPipelineConfigs,
        onLoad: addPipelineConfigs,
        lazy: true,
        visible: false,
      }));

      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'runningExecutions',
        visible: false,
        loader: loadRunningExecutions,
        onLoad: addRunningExecutions,
      }));
    }

  });
