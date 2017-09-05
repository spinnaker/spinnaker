import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';

import { Application } from '@spinnaker/core';

import { Provider, Store } from 'react-redux';
import { ICanaryState, rootReducer } from './reducers';
import { epicMiddleware } from './epics';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryConfigSummary, ICanaryMetricConfig } from './domain/index';
import { INITIALIZE } from './actions/index';
import { IJudge } from './domain/IJudge';
import Styleguide from './layout/styleguide';

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
        data: {
          application: props.app,
          configSummaries: props.app.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
          judges: props.app.getDataSource('canaryJudges').data as IJudge[],
        },
        selectedConfig: {
          config: null as ICanaryConfig,
          metricList: [] as ICanaryMetricConfig[],
          editingMetric: null as ICanaryMetricConfig,
          judge: { name: null } as IJudge,
          thresholds: {
            marginal: null,
            pass: null,
          },
          group: {
            selected: null,
            list: [] as string[],
          },
          load: {
            state: null,
          },
          save: {
            state: null,
            error: null,
          },
          destroy: {
            state: null,
            error: null,
          },
          json: {
            state: null,
            error: null,
          },
        },
        app: {
          deleteConfigModalOpen: false,
          editConfigJsonModalOpen: false,
        },
      }
    });
  }

  public render() {
    return (
      <Styleguide>
        <Provider store={this.store}>
          <CanaryConfigEdit/>
        </Provider>
      </Styleguide>
    );
  }
}
