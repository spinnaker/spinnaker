import { module } from 'angular';

import { StyleguideRoute } from './StyleguideRoute';
import { registerRootState } from '../navigation/rootState.registration';
import type { INestedState } from '../navigation/state.provider';

export const STYLEGUIDE_STATES = 'spinnaker.core.styleguide.states';

export function getStyleguideState(): INestedState {
  return {
    url: '/styleguide',
    name: 'styleguide',
    views: {
      'main@': {
        component: StyleguideRoute,
        $type: 'react',
      },
    },
    data: {
      pageTitleSection: {
        title: 'Styleguide',
      },
    },
  };
}

module(STYLEGUIDE_STATES, []);

registerRootState((stateConfigProvider) => stateConfigProvider.addToRootState(getStyleguideState()));
