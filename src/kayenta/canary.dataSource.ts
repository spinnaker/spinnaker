import { module } from 'angular';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry, DataSourceConfig, Application
} from '@spinnaker/core';

import { getCanaryConfigSummaries, listJudges } from './service/canaryConfig.service';
import { ICanaryConfigSummary, IJudge } from './domain/index';
import { canaryStore } from './canary';
import { UPDATE_CONFIG_SUMMARIES, UPDATE_JUDGES } from './actions/index';

export const CANARY_DATA_SOURCE = 'spinnaker.kayenta.canary.dataSource';
module(CANARY_DATA_SOURCE, [APPLICATION_DATA_SOURCE_REGISTRY])
  .run((applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    const loadCanaryConfigs = () => getCanaryConfigSummaries();

    const configsLoaded = (_application: Application, summaries: ICanaryConfigSummary[]) => {
      return Promise.resolve(summaries);
    };

    const afterConfigsLoad = (application: Application) => {
      canaryStore.dispatch({
        type: UPDATE_CONFIG_SUMMARIES,
        configSummaries: application.getDataSource('canaryConfigs').data,
      });
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      optional: true,
      primary: true,
      loader: loadCanaryConfigs,
      onLoad: configsLoaded,
      afterLoad: afterConfigsLoad,
      description: 'Canary analysis configuration and reporting',
      key: 'canaryConfigs',
      sref: '.canary.default',
      activeState: '**.canary.**',
      title: 'Canary',
      label: 'Canary',
      icon: 'bar-chart'
    }));

    const loadCanaryJudges = () => listJudges();

    const judgesLoaded = (_application: Application, judges: IJudge[]) => {
      return Promise.resolve(judges);
    };

    const afterJudgesLoad = (application: Application) => {
      canaryStore.dispatch({
        type: UPDATE_JUDGES,
        judges: application.getDataSource('canaryJudges').data,
      });
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'canaryJudges',
      loader: loadCanaryJudges,
      onLoad: judgesLoaded,
      afterLoad: afterJudgesLoad,
      lazy: false,
      visible: false,
    }));
  });
