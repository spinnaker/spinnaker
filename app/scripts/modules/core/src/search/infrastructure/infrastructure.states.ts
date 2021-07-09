import { module } from 'angular';
import { SETTINGS } from '../../config/settings';
import { STATE_CONFIG_PROVIDER, StateConfigProvider } from '../../navigation/state.provider';

export const INFRASTRUCTURE_STATES = 'spinnaker.core.search.states';
module(INFRASTRUCTURE_STATES, [STATE_CONFIG_PROVIDER]).config([
  'stateConfigProvider',
  (stateConfigProvider: StateConfigProvider) => {
    stateConfigProvider.addToRootState({
      name: 'search',
      url: '/search?q&key&tab&name&account&region&stack',
      params: {
        account: { dynamic: true, value: null },
        key: { dynamic: true, value: null },
        name: { dynamic: true, value: null },
        q: { dynamic: true, value: null },
        region: { dynamic: true, value: null },
        stack: { dynamic: true, value: null },
        tab: { dynamic: true, value: null },
      },
      views: {
        'main@': {
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
    stateConfigProvider.addRewriteRule('/infrastructure?q', '/search?q');
    stateConfigProvider.addRewriteRule('', '/search');
    stateConfigProvider.addRewriteRule('/', '/search');
  },
]);
