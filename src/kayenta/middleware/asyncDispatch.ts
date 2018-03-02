import { Middleware, MiddlewareAPI, Dispatch, Action } from 'redux';

import { ICanaryState } from '../reducers/index';

/*
* Middleware for scheduling actions from within reducers.
* Provides every action with an `asyncDispatch` method.
* Actions provided to `asyncDispatch` will run after all reducers have completed.
* TODO: type actions to include `asyncDispatch`.
* */

// TODO: replace the `any` generic passed to MiddlewareAPI with ICanaryState. The Redux typings here are wrong.
// Should be fixed in this PR: https://github.com/reactjs/redux/pull/2563
export const asyncDispatchMiddleware: Middleware = (store: MiddlewareAPI<any>) => (next: Dispatch<ICanaryState>) => (action: Action & any) => {
  let syncActivityFinished = false;
  let actionQueue: Action[] = [];

  const flushQueue = () => {
    actionQueue.forEach(a => store.dispatch(a));
    actionQueue = [];
  };

  const asyncDispatch = (a: Action & any) => {
    actionQueue.push(a);
    if (syncActivityFinished) {
      flushQueue();
    }
  };

  const nextAction = next({ ...action, asyncDispatch });
  syncActivityFinished = true;
  flushQueue();

  return nextAction;
};
