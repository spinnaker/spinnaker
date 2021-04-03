import { CanarySettings } from 'kayenta/canary.settings';

import { Application, ApplicationDataSourceRegistry } from '@spinnaker/core';

import { getCanaryConfigSummaries, listJudges } from './service/canaryConfig.service';
import { listCanaryExecutions } from './service/canaryRun.service';

// When the lazy portion loads, it overwrites these stub with implementation that bridges data to the redux store
export const stub = {
  loadCanaryExecutions: (application: Application) => listCanaryExecutions(application.name),
  afterConfigsLoaded: (_application: Application): void => void 0,
  afterJudgesLoaded: (_application: Application): void => void 0,
  afterCanaryExecutionsLoaded: (_application: Application): void => void 0,
};

export function registerKayentaDataSourceStubs() {
  const onLoad = (app: Application, data: any) => Promise.resolve(data);
  const loadCanaryConfigs = (application: Application) => {
    return CanarySettings.showAllConfigs ? getCanaryConfigSummaries() : getCanaryConfigSummaries(application.name);
  };

  ApplicationDataSourceRegistry.registerDataSource({
    optIn: !CanarySettings.optInAll,
    optional: true,
    loader: loadCanaryConfigs,
    onLoad,
    afterLoad: (application) => stub.afterConfigsLoaded(application),
    description: 'Canary analysis configuration and reporting',
    key: 'canaryConfigs',
    label: 'Canary',
    defaultData: [],
  });

  ApplicationDataSourceRegistry.registerDataSource({
    key: 'canaryJudges',
    label: 'Canary Configs',
    sref: '.canary.canaryConfig',
    activeState: '**.canaryConfig.**',
    category: 'delivery',
    requiresDataSource: 'canaryConfigs',
    loader: listJudges,
    onLoad,
    afterLoad: (application) => stub.afterJudgesLoaded(application),
    lazy: true,
    autoActivate: true,
    defaultData: [],
    iconName: 'spMenuCanaryConfig',
  });

  ApplicationDataSourceRegistry.registerDataSource({
    key: 'canaryExecutions',
    label: 'Canary Reports',
    sref: '.canary.report',
    activeState: '**.report.**',
    category: 'delivery',
    requiresDataSource: 'canaryConfigs',
    loader: (application) => stub.loadCanaryExecutions(application),
    onLoad,
    afterLoad: (application) => stub.afterCanaryExecutionsLoaded(application),
    lazy: true,
    defaultData: [],
    iconName: 'spMenuCanaryReport',
  });
}
