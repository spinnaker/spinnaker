import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import { Provider, Store } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';

import { Application } from '@spinnaker/core';

import CanaryConfigEdit from './edit/edit';
import { ICanaryConfig, ICanaryMetricConfig } from './domain/ICanaryConfig';
import { getCanaryConfigById } from './service/canaryConfig.service';

export interface ICanaryProps {
  application: Application;
}

interface StoreState {
  metricList: ICanaryMetricConfig[],
  selectedMetric: ICanaryMetricConfig
}

// TODO: better packaging for actions, reducers, epics

const selectConfigEpic = (action$: Observable<any>) =>
  action$
    .filter((action: any) => action.type === 'load_config')
    .concatMap(action => getCanaryConfigById(action.id))
    .map(config => ({ type: 'select_config', config }));

const rootEpic = combineEpics(
  selectConfigEpic
);

const epicMiddleware = createEpicMiddleware(rootEpic);

function rootReducer(state: StoreState, action: any) {
  switch (action.type) {
    case 'initialize':
      return action.state;
    case 'select_config':
      return Object.assign({}, state, {
        selectedConfig: action.config,
        metricList: action.config.metrics,
        selectedMetric: null
      });
    default:
      return state;
  }
}

export class Canary extends React.Component<ICanaryProps, {}> {

  private store: Store<StoreState>;

  constructor(props: ICanaryProps) {
    super();
    const configSummaries = props.application.getDataSource('canaryConfigs').data as ICanaryConfig[];
    this.store = createStore<StoreState>(
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
