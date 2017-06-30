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
    const configSummaries = props.app.getDataSource('canaryConfigs').data as ICanaryConfigSummary[];
    this.store = createStore<ICanaryState>(
      rootReducer,
      applyMiddleware(epicMiddleware)
    );
    this.store.dispatch({
      type: 'initialize',
      state: {
        configSummaries,
        selectedConfig: null as ICanaryConfig,
        configLoadState: ConfigDetailLoadState.Loaded,
        metricList: [] as ICanaryMetricConfig[],
        selectedMetric: null as ICanaryMetricConfig
      }
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
