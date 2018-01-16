import { module } from 'angular';

import { STATE_CONFIG_PROVIDER, StateConfigProvider } from 'core/navigation/state.provider';
import { SETTINGS } from 'core/config/settings';

export const INFRASTRUCTURE_STATES = 'spinnaker.core.search.states';
module(INFRASTRUCTURE_STATES, [
  STATE_CONFIG_PROVIDER
]).config((stateConfigProvider: StateConfigProvider) => {
  'ngInject';

  stateConfigProvider.addToRootState({
    name: 'search',
    url: '/search?q&key&tab&name&account&region&stack',
    params: {
      account: { dynamic: true, inherit: false, value: null },
      key: { dynamic: true, inherit: false, value: null },
      name: { dynamic: true, inherit: false, value: null },
      q: { dynamic: true, inherit: false, value: null },
      region: { dynamic: true, inherit: false, value: null },
      stack: { dynamic: true, inherit: false, value: null },
      tab: { dynamic: true, inherit: true, value: null },
    },
    views: {
      'main@': {
        template: `
          <infrastructure-search-v1 ng-if="$resolve.version == 1" class="flex-fill"></infrastructure-search-v1>
          <infrastructure-search-v2 ng-if="$resolve.version == 2" class="flex-fill"></infrastructure-search-v2>
        `,
      }
    },
    data: {
      pageTitleMain: {
        label: 'Search'
      }
    },
    resolve: {
      version: () => SETTINGS.searchVersion || 1,
    }
  });

  stateConfigProvider.addToRootState({ name: 'infrastructure', url: '/search?q', redirectTo: 'home.search' });
  stateConfigProvider.addRewriteRule('/infrastructure?q', '/search?q');
  stateConfigProvider.addRewriteRule('', '/search');
  stateConfigProvider.addRewriteRule('/', '/search');
});
