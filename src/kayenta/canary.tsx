import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';

import { Application } from '@spinnaker/core';

import { Provider, Store } from 'react-redux';
import { ICanaryState, rootReducer } from './reducers';
import { epicMiddleware } from './epics';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryConfigSummary, ICanaryMetricConfig } from './domain/index';
import { ConfigDetailLoadState } from './edit/configDetailLoader';
import { INITIALIZE } from './actions/index';
import { SaveConfigState } from './edit/save';
import { DeleteConfigState } from './edit/deleteModal';

export interface ICanaryProps {
  app: Application;
}

export const canaryStore = createStore<ICanaryState>(
  rootReducer,
  applyMiddleware(epicMiddleware)
);

export default class Canary extends React.Component<ICanaryProps, {}> {

  private store: Store<ICanaryState>;

  constructor(props: ICanaryProps) {
    super();
    this.store = canaryStore;
    this.store.dispatch({
      type: INITIALIZE,
      state: {
        application: props.app,
        configSummaries: props.app.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
        selectedConfig: null as ICanaryConfig,
        configLoadState: ConfigDetailLoadState.Loading,
        metricList: [] as ICanaryMetricConfig[],
        saveConfigState: SaveConfigState.Saved,
        deleteConfigState: DeleteConfigState.Completed,
      }
    });
  }

  public render() {
    return (
      <div className="styleguide">
        <Provider store={this.store}>
          <CanaryConfigEdit/>
        </Provider>
      </div>
    );
  }
}
