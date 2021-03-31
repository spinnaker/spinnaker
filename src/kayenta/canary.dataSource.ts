import { IQService, module } from 'angular';
import { CanarySettings } from 'kayenta/canary.settings';

import { Application, ApplicationDataSourceRegistry } from '@spinnaker/core';

import * as Creators from './actions/creators';
import { canaryStore } from './canary';
import { ICanaryExecutionStatusResult } from './domain/ICanaryExecutionStatusResult';
import { ICanaryConfigSummary, IJudge } from './domain/index';
import { getCanaryConfigSummaries, listJudges } from './service/canaryConfig.service';
import { listCanaryExecutions } from './service/canaryRun.service';

export const CANARY_DATA_SOURCE = 'spinnaker.kayenta.canary.dataSource';
module(CANARY_DATA_SOURCE, []).run([
  '$q',
  ($q: IQService) => {
    'ngInject';

    if (CanarySettings.featureDisabled) {
      return;
    }

    // TODO: IDataSourceConfig expects an IPromise (not a Promise) from the loaders in this function, which is why we're using $q.resolve(...).
    const loadCanaryConfigs = (application: Application) => {
      const request = CanarySettings.showAllConfigs
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
      canaryStore.dispatch(
        Creators.updateConfigSummaries({
          configSummaries: application.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
        }),
      );
    };

    ApplicationDataSourceRegistry.registerDataSource({
      optIn: !CanarySettings.optInAll,
      optional: true,
      loader: loadCanaryConfigs,
      onLoad: configsLoaded,
      afterLoad: afterConfigsLoad,
      description: 'Canary analysis configuration and reporting',
      key: 'canaryConfigs',
      label: 'Canary',
      defaultData: [],
    });

    const loadCanaryJudges = () => $q.resolve(listJudges());

    const judgesLoaded = (_application: Application, judges: IJudge[]) => {
      return $q.resolve(judges);
    };

    const afterJudgesLoad = (application: Application) => {
      if (application !== canaryStore.getState().data.application) {
        return;
      }
      canaryStore.dispatch(
        Creators.updateJudges({
          judges: application.getDataSource('canaryJudges').data as IJudge[],
        }),
      );
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'canaryJudges',
      label: 'Canary Configs',
      sref: '.canary.canaryConfig',
      activeState: '**.canaryConfig.**',
      category: 'delivery',
      requiresDataSource: 'canaryConfigs',
      loader: loadCanaryJudges,
      onLoad: judgesLoaded,
      afterLoad: afterJudgesLoad,
      lazy: true,
      autoActivate: true,
      defaultData: [],
      iconName: 'spMenuCanaryConfig',
    });

    const loadCanaryExecutions = (application: Application) => {
      const listExecutionsRequest = listCanaryExecutions(application.name);

      listExecutionsRequest.catch((error) => {
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
      canaryStore.dispatch(
        Creators.loadExecutionsSuccess({
          executions: application.getDataSource('canaryExecutions').data as ICanaryExecutionStatusResult[],
        }),
      );
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'canaryExecutions',
      label: 'Canary Reports',
      sref: '.canary.report',
      activeState: '**.report.**',
      category: 'delivery',
      requiresDataSource: 'canaryConfigs',
      loader: loadCanaryExecutions,
      onLoad: canaryExecutionsLoaded,
      afterLoad: afterCanaryExecutionsLoaded,
      lazy: true,
      defaultData: [],
      iconName: 'spMenuCanaryReport',
    });
  },
]);
