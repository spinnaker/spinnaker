import type { Action, Dispatch, Middleware } from 'redux';

import type { ICanaryState } from '../reducers/index';

/*
 * Middleware for scheduling actions from within reducers.
 * Provides every action with an `asyncDispatch` method.
 * Actions provided to `asyncDispatch` will run after all reducers have completed.
 * TODO: type actions to include `asyncDispatch`.
 * */

export const asyncDispatchMiddleware: Middleware<{}, ICanaryState> = (store) => (next: Dispatch) => (
  action: Action & any,
) => {
  let syncActivityFinished = false;
  let actionQueue: Action[] = [];

  const flushQueue = () => {
    actionQueue.forEach((a) => store.dispatch(a));
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
