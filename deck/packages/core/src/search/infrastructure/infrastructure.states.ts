import { module } from 'angular';

import { SearchV1 } from './SearchV1';
import { SearchV2 } from './SearchV2';
import { SETTINGS } from '../../config/settings';
import { registerRootState } from '../../navigation/rootState.registration';
import type { StateConfigProvider } from '../../navigation/state.provider';
import { STATE_CONFIG_PROVIDER } from '../../navigation/state.provider';

export const INFRASTRUCTURE_STATES = 'spinnaker.core.search.states';

function registerSearchStates(stateConfigProvider: StateConfigProvider, directReact = false): void {
  stateConfigProvider.addToRootState({
    name: 'search',
    url: '/search?q&key&tab&name&account&region&stack&route',
    params: {
      account: { dynamic: true, value: null },
      key: { dynamic: true, value: null },
      name: { dynamic: true, value: null },
      q: { dynamic: true, value: null },
      region: { dynamic: true, value: null },
      route: { dynamic: true, value: null },
      stack: { dynamic: true, value: null },
      tab: { dynamic: true, value: null },
    },
    views: {
      'main@': directReact
        ? {
            component: SETTINGS.searchVersion === 2 ? SearchV2 : SearchV1,
            $type: 'react',
          }
        : {
            template: `
          <infrastructure-search-v1 ng-if="$resolve.version == 1" class="flex-fill"></infrastructure-search-v1>
          <infrastructure-search-v2 ng-if="$resolve.version == 2" class="flex-fill"></infrastructure-search-v2>
        `,
          },
    },
    data: {
      pageTitleMain: {
        label: 'Search',
      },
    },
    resolve: {
      version: () => SETTINGS.searchVersion || 1,
    },
  });

  stateConfigProvider.addToRootState({ name: 'infrastructure', url: '/search?q', redirectTo: 'home.search' });
  if (!stateConfigProvider.addRewriteRule) {
    return;
  }
  stateConfigProvider.addRewriteRule('/infrastructure?q', '/search?q');
  stateConfigProvider.addRewriteRule('', '/search');
  stateConfigProvider.addRewriteRule('/', '/search');
}

registerRootState((stateConfigProvider) => registerSearchStates(stateConfigProvider, true));

module(INFRASTRUCTURE_STATES, [STATE_CONFIG_PROVIDER]).config([
  'stateConfigProvider',
  (stateConfigProvider: StateConfigProvider) => {
    registerSearchStates(stateConfigProvider);
  },
]);
