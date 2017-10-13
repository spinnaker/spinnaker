import { module, IQService } from 'angular';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry,
  Application
} from '@spinnaker/core';

import { getCanaryConfigSummaries, listJudges } from './service/canaryConfig.service';
import { ICanaryConfigSummary, IJudge } from './domain/index';
import { canaryStore } from './canary';
import * as Creators from './actions/creators';
import { getCanaryJudgeResultSummaries } from './service/canaryJudgeResult.service';
import { ICanaryJudgeResultSummary } from './domain/ICanaryJudgeResultSummary';

export const CANARY_DATA_SOURCE = 'spinnaker.kayenta.canary.dataSource';
module(CANARY_DATA_SOURCE, [APPLICATION_DATA_SOURCE_REGISTRY])
  .run(($q: IQService, applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    // TODO: IDataSourceConfig expects an IPromise (not a Promise) from the loaders in this function, which is why we're using $q.resolve(...).
    const loadCanaryConfigs = () => $q.resolve(getCanaryConfigSummaries());

    const configsLoaded = (_application: Application, summaries: ICanaryConfigSummary[]) => {
      return $q.resolve(summaries);
    };

    const afterConfigsLoad = (application: Application) => {
      canaryStore.dispatch(Creators.updateConfigSummaries({
        configSummaries: application.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
      }));
    };

    applicationDataSourceRegistry.registerDataSource({
      optional: true,
      primary: true,
      loader: loadCanaryConfigs,
      onLoad: configsLoaded,
      afterLoad: afterConfigsLoad,
      description: 'Canary analysis configuration and reporting',
      key: 'canaryConfigs',
      sref: '.canary.canaryConfig.configDefault',
      activeState: '**.canary.**',
      label: 'Canary',
      icon: 'bar-chart'
    });

    const loadCanaryJudges = () => $q.resolve(listJudges());

    const judgesLoaded = (_application: Application, judges: IJudge[]) => {
      return $q.resolve(judges);
    };

    const afterJudgesLoad = (application: Application) => {
      canaryStore.dispatch(Creators.updateJudges({
        judges: application.getDataSource('canaryJudges').data as IJudge[],
      }));
    };

    applicationDataSourceRegistry.registerDataSource({
      key: 'canaryJudges',
      loader: loadCanaryJudges,
      onLoad: judgesLoaded,
      afterLoad: afterJudgesLoad,
      lazy: false,
      visible: false,
    });

    const loadJudgeResults = () => $q.resolve(getCanaryJudgeResultSummaries());

    const resultsLoaded = (_application: Application, summaries: ICanaryJudgeResultSummary[]) =>
      $q.resolve(summaries);

    const afterResultsLoaded = (application: Application) =>
      canaryStore.dispatch(Creators.updateResultSummaries({
        resultSummaries: application.getDataSource('canaryJudgeResults').data as ICanaryJudgeResultSummary[],
      }));

    applicationDataSourceRegistry.registerDataSource({
      key: 'canaryJudgeResults',
      loader: loadJudgeResults,
      onLoad: resultsLoaded,
      afterLoad: afterResultsLoaded,
      lazy: false,
      visible: false,
    });
  });
