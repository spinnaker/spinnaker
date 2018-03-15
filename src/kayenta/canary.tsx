import * as React from 'react';
import { createStore, applyMiddleware } from 'redux';
import { logger } from 'redux-logger';
import { UIView } from '@uirouter/react';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import { Application, ReactInjector } from '@spinnaker/core';

import { Provider, Store } from 'react-redux';
import { ICanaryState, rootReducer } from './reducers';
import { actionInterceptingMiddleware, epicMiddleware, asyncDispatchMiddleware } from './middleware';
import { ICanaryConfigSummary, IJudge } from './domain';
import { INITIALIZE } from './actions';
import Styleguide from './layout/styleguide';
import { CanarySettings } from './canary.settings';
import CanaryHeader from './navigation/canaryHeader';
import { canaryTabs } from './navigation/canaryTabs';
import * as Creators from 'kayenta/actions/creators';

export interface ICanaryProps {
  app: Application;
  success$: Observable<void>;
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
  private subscription: Subscription;

  constructor(props: ICanaryProps) {
    super(props);
    this.store = canaryStore;
    this.initializeAppState(props.app);
    this.subscription = this.props.success$.subscribe(() => {
      const tab = canaryTabs.find(t =>
        t.activeStates.some(s => ReactInjector.$state.includes(s)));
      this.store.dispatch(Creators.setActiveTab({ tab: tab.title }));
    });
  }

  public componentWillReceiveProps(nextProps: ICanaryProps) {
    if (this.props.app.name !== nextProps.app.name) {
      this.initializeAppState(nextProps.app)
    }
  }

  public componentWillUnmount(): void {
    this.subscription.unsubscribe();
  }

  private initializeAppState(app: Application): void {
    this.store.dispatch({
      type: INITIALIZE,
      state: {
        data: {
          application: app,
          configSummaries: app.getDataSource('canaryConfigs').data as ICanaryConfigSummary[],
          judges: app.getDataSource('canaryJudges').data as IJudge[],
        },
      },
    });
  }

  public render() {
    const noWrap = { wrap: false };
    return (
      <Styleguide className="kayenta-root vertical">
        <Provider store={this.store}>
          <div className="vertical flex-1">
            <CanaryHeader tabs={canaryTabs} title="Canary"/>
            <UIView {...noWrap} name="canary"/>
          </div>
        </Provider>
      </Styleguide>
    );
  }
}
