import { Application } from '@spinnaker/core';

import * as Creators from './actions/creators';
import { canaryStore } from './canary';
import { stub } from './canary.dataSource.stub';
import { ICanaryExecutionStatusResult } from './domain/ICanaryExecutionStatusResult';
import { ICanaryConfigSummary, IJudge } from './domain/index';
import { listCanaryExecutions } from './service/canaryRun.service';

export function bridgeKayentaDataSourceToReduxStore() {
  stub.afterConfigsLoaded = (application: Application) => {
    if (application !== canaryStore.getState().data.application) {
      return;
    }
    canaryStore.dispatch(
      Creators.updateConfigSummaries({
        configSummaries: application.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
      }),
    );
  };

  stub.afterJudgesLoaded = (application: Application) => {
    if (application !== canaryStore.getState().data.application) {
      return;
    }
    canaryStore.dispatch(
      Creators.updateJudges({
        judges: application.getDataSource('canaryJudges').data as IJudge[],
      }),
    );
  };

  stub.loadCanaryExecutions = (application: Application) => {
    const listExecutionsRequest = listCanaryExecutions(application.name);

    listExecutionsRequest.catch((error) => {
      canaryStore.dispatch(Creators.loadExecutionsFailure({ error }));
    });

    return listExecutionsRequest;
  };

  stub.afterCanaryExecutionsLoaded = (application: Application) => {
    if (application !== canaryStore.getState().data.application) {
      return;
    }
    canaryStore.dispatch(
      Creators.loadExecutionsSuccess({
        executions: application.getDataSource('canaryExecutions').data as ICanaryExecutionStatusResult[],
      }),
    );
  };
}
