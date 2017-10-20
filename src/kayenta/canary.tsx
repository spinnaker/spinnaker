import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { logger } from 'redux-logger';
import { UIView } from '@uirouter/react';

import { Application } from '@spinnaker/core';

import { Provider, Store } from 'react-redux';
import { ICanaryState, rootReducer } from './reducers';
import { actionInterceptingMiddleware, epicMiddleware, asyncDispatchMiddleware } from './middleware';
import { ICanaryConfigSummary } from './domain/index';
import { INITIALIZE } from './actions/index';
import { IJudge } from './domain/IJudge';
import Styleguide from './layout/styleguide';
import { CanarySettings } from './canary.settings';
import { CanaryHeader } from './navigation/canaryHeader';
import { canaryTabs } from './navigation/canaryTabs';

export interface ICanaryProps {
  app: Application;
}

const middleware = [epicMiddleware, actionInterceptingMiddleware, asyncDispatchMiddleware];

export const canaryStore: Store<ICanaryState> = createStore<ICanaryState>(
  rootReducer,
  applyMiddleware(
    ...(CanarySettings.reduxLogger ? [...middleware, logger] : middleware)
  )
);

export default class Canary extends React.Component<ICanaryProps> {

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
    const noWrap = { wrap: false };
    return (
      <Styleguide className="kayenta-root">
        <Provider store={this.store}>
          <div>
            <CanaryHeader tabs={canaryTabs} title="Canary"/>
            <UIView {...noWrap} name="canary"/>
          </div>
        </Provider>
      </Styleguide>
    );
  }
}
