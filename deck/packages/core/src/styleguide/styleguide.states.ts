import { module } from 'angular';

import { StyleguideRoute } from './StyleguideRoute';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { STATE_CONFIG_PROVIDER } from '../navigation/state.provider';

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

module(STYLEGUIDE_STATES, [STATE_CONFIG_PROVIDER]).config([
  'stateConfigProvider',
  (stateConfigProvider: StateConfigProvider) => {
    stateConfigProvider.addToRootState(getStyleguideState());
  },
]);
