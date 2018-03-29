import { module, IQService } from 'angular';
import { CanarySettings } from 'kayenta/canary.settings';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry,
  Application
} from '@spinnaker/core';

import { getCanaryConfigSummaries, listJudges } from './service/canaryConfig.service';
import { ICanaryConfigSummary, IJudge } from './domain/index';
import { canaryStore } from './canary';
import * as Creators from './actions/creators';
import { listCanaryExecutions } from './service/canaryRun.service';
import { ICanaryExecutionStatusResult } from './domain/ICanaryExecutionStatusResult';

export const CANARY_DATA_SOURCE = 'spinnaker.kayenta.canary.dataSource';
module(CANARY_DATA_SOURCE, [APPLICATION_DATA_SOURCE_REGISTRY])
  .run(($q: IQService, applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    // TODO: IDataSourceConfig expects an IPromise (not a Promise) from the loaders in this function, which is why we're using $q.resolve(...).
    const loadCanaryConfigs = (application: Application) => {
      const request =
        CanarySettings.showAllConfigs
          ? getCanaryConfigSummaries()
          : getCanaryConfigSummaries(application.name);
      return $q.resolve(request);
    };

    const configsLoaded = (_application: Application, summaries: ICanaryConfigSummary[]) => {
      return $q.resolve(summaries);
    };

    const afterConfigsLoad = (application: Application) => {
      if (application !== canaryStore.getState().data.application) {
        return;
      }
      canaryStore.dispatch(Creators.updateConfigSummaries({
        configSummaries: application.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
      }));
    };

    applicationDataSourceRegistry.registerDataSource({
      optIn: !CanarySettings.optInAll,
      optional: true,
      loader: loadCanaryConfigs,
      onLoad: configsLoaded,
      afterLoad: afterConfigsLoad,
      description: 'Canary analysis configuration and reporting',
      key: 'canaryConfigs',
      label: 'Canary'
    });

    const loadCanaryJudges = () => $q.resolve(listJudges());

    const judgesLoaded = (_application: Application, judges: IJudge[]) => {
      return $q.resolve(judges);
    };

    const afterJudgesLoad = (application: Application) => {
      if (application !== canaryStore.getState().data.application) {
        return;
      }
      canaryStore.dispatch(Creators.updateJudges({
        judges: application.getDataSource('canaryJudges').data as IJudge[],
      }));
    };

    applicationDataSourceRegistry.registerDataSource({
      key: 'canaryJudges',
      label: 'Canary Configs',
      sref: '.canary.canaryConfig',
      activeState: '**.canaryConfig.**',
      category: 'delivery',
      requiresDataSource: 'canaryConfigs',
      loader: loadCanaryJudges,
      onLoad: judgesLoaded,
      afterLoad: afterJudgesLoad,
      lazy: false,
    });

    const loadCanaryExecutions = (application: Application) => {
      // TODO(dpeach): make the number of canary executions rendered configurable from the UI.
      const listExecutionsRequest = listCanaryExecutions(application.name, 20);

      listExecutionsRequest.catch(error => {
        canaryStore.dispatch(Creators.loadExecutionsFailure({ error }));
      });

      return $q.resolve(listExecutionsRequest);
    };

    const canaryExecutionsLoaded = (_application: Application, executions: ICanaryExecutionStatusResult[]) => {
      return $q.resolve(executions);
    };

    const afterCanaryExecutionsLoaded = (application: Application) => {
      if (application !== canaryStore.getState().data.application) {
        return;
      }
      canaryStore.dispatch(Creators.loadExecutionsSuccess({
        executions: application.getDataSource('canaryExecutions').data as ICanaryExecutionStatusResult[],
      }));
    };

    applicationDataSourceRegistry.registerDataSource({
      key: 'canaryExecutions',
      label: 'Canary Reports',
      sref: '.canary.report',
      activeState: '**.report.**',
      category: 'delivery',
      requiresDataSource: 'canaryConfigs',
      loader: loadCanaryExecutions,
      onLoad: canaryExecutionsLoaded,
      afterLoad: afterCanaryExecutionsLoaded,
      lazy: false,
    });
  });
