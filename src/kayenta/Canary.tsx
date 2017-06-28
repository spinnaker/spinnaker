import * as React from 'react';
import { createStore } from 'redux';
import { Provider, Store } from 'react-redux';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryMetricConfig } from './domain/ICanaryConfig';

import { Application } from '@spinnaker/core';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');

export interface ICanaryProps {
  application: Application;
}

interface StoreState {
  metricList: ICanaryMetricConfig[],
  selectedMetric: ICanaryMetricConfig
}

function reducers(state: StoreState) {
  return state;
}

export class Canary extends React.Component<ICanaryProps, {}> {

  private store: Store<StoreState>;

  constructor(props: ICanaryProps) {
    super();
    const configSummaries = props.application.getDataSource('canaryConfigs').data as ICanaryConfig[];
    // TODO: need appropriate config loading code
    const config = atlasCanaryConfig;
    const initialState = {
      configSummaries,
      selectedConfig: config,
      metricList: config.metrics,
      selectedMetric: null as ICanaryMetricConfig
    };
    this.store = createStore<StoreState>(reducers, initialState);
  }

  public render() {
    return (
      <Provider store={this.store}>
        <CanaryConfigEdit/>
      </Provider>
    );
  }
}
