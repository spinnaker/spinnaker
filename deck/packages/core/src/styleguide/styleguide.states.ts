import { StyleguideRoute } from './StyleguideRoute';
import { registerRootState } from '../navigation/rootState.registration';
import type { INestedState } from '../navigation/state.provider';

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

registerRootState((stateConfigProvider) => stateConfigProvider.addToRootState(getStyleguideState()));
