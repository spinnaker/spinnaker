import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { Provider, Store } from 'react-redux';

import { Application } from '@spinnaker/core';

import { ICanaryState, rootReducer } from './reducers';
import { epicMiddleware } from './epics';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryConfigSummary, ICanaryMetricConfig } from './domain/index';
import { ConfigDetailLoadState } from './edit/configDetailLoader';

export interface ICanaryProps {
  app: Application;
}

export default class Canary extends React.Component<ICanaryProps, {}> {

  private store: Store<ICanaryState>;

  constructor(props: ICanaryProps) {
    super();
    this.store = createStore<ICanaryState>(
      rootReducer,
      applyMiddleware(epicMiddleware)
    );
    this.store.dispatch({
      type: 'initialize',
      state: {
        configSummaries: [] as ICanaryConfigSummary[],
        selectedConfig: null as ICanaryConfig,
        configLoadState: ConfigDetailLoadState.Loaded,
        metricList: [] as ICanaryMetricConfig[],
        selectedMetric: null as ICanaryMetricConfig
      }
    });

    props.app.getDataSource('canaryConfigs').ready().then(() => {
      const summaries: ICanaryConfigSummary[] = props.app.getDataSource('canaryConfigs').data;
      this.store.dispatch({
        type: 'update_config_summaries',
        configSummaries: summaries,
      });
    });
  }

  public render() {
    return (
      <Provider store={this.store}>
        <CanaryConfigEdit/>
      </Provider>
    );
  }
}
