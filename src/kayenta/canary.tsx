import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { logger } from 'redux-logger';

import { Application } from '@spinnaker/core';

import { Provider, Store } from 'react-redux';
import { ICanaryState, rootReducer } from './reducers';
import { actionInterceptingMiddleware, epicMiddleware } from './middleware';
import CanaryConfigEdit from './edit/edit';
import { ICanaryConfigSummary } from './domain/index';
import { INITIALIZE } from './actions/index';
import { IJudge } from './domain/IJudge';
import Styleguide from './layout/styleguide';
import { CanarySettings } from './canary.settings';

export interface ICanaryProps {
  app: Application;
}

const middleware = [epicMiddleware, actionInterceptingMiddleware];

export const canaryStore = createStore<ICanaryState>(
  rootReducer,
  applyMiddleware(
    ...(CanarySettings.reduxLogger ? [...middleware, logger] : middleware)
  )
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
      },
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
