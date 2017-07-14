import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { Provider, Store } from 'react-redux';

import { Application } from '@spinnaker/core';

import { ICanaryState, rootReducer } from './reducers';
import { epicMiddleware } from './epics';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryConfigSummary, ICanaryMetricConfig } from './domain/index';
import { ConfigDetailLoadState } from './edit/configDetailLoader';
import { INITIALIZE, UPDATE_CONFIG_SUMMARIES } from './actions/index';
import { SaveConfigState } from './edit/save';

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
      type: INITIALIZE,
      state: {
        configSummaries: [] as ICanaryConfigSummary[],
        selectedConfig: null as ICanaryConfig,
        configLoadState: ConfigDetailLoadState.Loaded,
        metricList: [] as ICanaryMetricConfig[],
        saveConfigState: SaveConfigState.Saved,
      }
    });

    props.app.getDataSource('canaryConfigs').ready().then(() => {
      const summaries: ICanaryConfigSummary[] = props.app.getDataSource('canaryConfigs').data;
      this.store.dispatch({
        type: UPDATE_CONFIG_SUMMARIES,
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
