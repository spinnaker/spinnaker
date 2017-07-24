import { module, IQService } from 'angular';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry, DataSourceConfig, Application
} from '@spinnaker/core';

import { getCanaryConfigSummaries } from './service/canaryConfig.service';
import { ICanaryConfigSummary } from './domain/index';
import { canaryStore } from './canary';
import { UPDATE_CONFIG_SUMMARIES } from './actions/index';

export const CANARY_DATA_SOURCE = 'spinnaker.kayenta.canary.dataSource';
module(CANARY_DATA_SOURCE, [APPLICATION_DATA_SOURCE_REGISTRY])
  .run(($q: IQService, applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    const loadCanaryConfigs = () => getCanaryConfigSummaries();

    const configsLoaded = (_application: Application, summaries: ICanaryConfigSummary[]) => {
      return $q.resolve(summaries);
    };

    const afterLoad = (application: Application) => {
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
      afterLoad: afterLoad,
      description: 'Canary analysis configuration and reporting',
      key: 'canaryConfigs',
      sref: '.canary',
      title: 'Canary',
      label: 'Canary',
      icon: 'bar-chart'
    }));
  });
