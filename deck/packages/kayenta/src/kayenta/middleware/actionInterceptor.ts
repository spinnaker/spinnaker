import type { Action, Dispatch, Middleware } from 'redux';

import * as Creators from '../actions/creators';
import * as Actions from '../actions/index';
import type { ICanaryState } from '../reducers/index';
import { buildConfigCopy, buildNewConfig } from '../service/canaryConfig.service';

export const actionInterceptingMiddleware: Middleware<{}, ICanaryState> = (store) => (next: Dispatch) => (
  action: Action & any,
) => {
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
