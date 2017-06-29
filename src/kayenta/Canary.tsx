import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { Provider, Store } from 'react-redux';

import { Application } from '@spinnaker/core';

import { ICanaryState, rootReducer } from './reducers';
import { epicMiddleware } from './epics';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryMetricConfig } from './domain/ICanaryConfig';

export interface ICanaryProps {
  application: Application;
}

export class Canary extends React.Component<ICanaryProps, {}> {

  private store: Store<ICanaryState>;

  constructor(props: ICanaryProps) {
    super();
    const configSummaries = props.application.getDataSource('canaryConfigs').data as ICanaryConfig[];
    this.store = createStore<ICanaryState>(
      rootReducer,
      applyMiddleware(epicMiddleware)
    );
    this.store.dispatch({
      type: 'initialize',
      state: {
        configSummaries,
        selectedConfig: null as ICanaryConfig,
        metricList: [] as ICanaryMetricConfig[],
        selectedMetric: null as ICanaryMetricConfig
      }
    });
    this.store.dispatch({
      type: 'load_config',
      id: 'mysampleatlascanaryconfig'
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
