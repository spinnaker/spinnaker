import { Middleware, MiddlewareAPI, Dispatch, Action } from 'redux';

import * as Actions from 'kayenta/actions/index';
import * as Creators from 'kayenta/actions/creators';
import {
  buildConfigCopy,
  buildNewConfig
} from '../service/canaryConfig.service';
import { ICanaryState } from '../reducers/index';

// TODO: replace the `any` generic passed to MiddlewareAPI with ICanaryState. The Redux typings here are wrong.
// Should be fixed in this PR: https://github.com/reactjs/redux/pull/2563
export const actionInterceptingMiddleware: Middleware = (store: MiddlewareAPI<any>) => (next: Dispatch<ICanaryState>) => (action: Action & any) => {
  switch (action.type) {
    case Actions.CREATE_NEW_CONFIG: {
      const newConfig = buildNewConfig(store.getState());
      const newAction = Creators.selectConfig({ config: newConfig });
      return next(newAction);
    }

    case Actions.COPY_SELECTED_CONFIG: {
      const copiedConfig = buildConfigCopy(store.getState());
      const newAction = Creators.selectConfig({ config: copiedConfig });
      return next(newAction);
    }

    default:
      return next(action);
  }
};
