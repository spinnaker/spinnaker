import { module } from 'angular';

import { AccountManagementPageContainer } from './AccountManagementPageContainer';
import type { INestedState, StateConfigProvider } from '../../navigation/state.provider';
import { STATE_CONFIG_PROVIDER } from '../../navigation/state.provider';

export const ACCOUNT_MANAGEMENT_STATES = 'spinnaker.core.accountManagement.states';
module(ACCOUNT_MANAGEMENT_STATES, [STATE_CONFIG_PROVIDER]).config([
  'stateConfigProvider',
  (stateConfigProvider: StateConfigProvider) => {
    const accountManagement: INestedState = {
      name: 'accountManagement',
      url: '/account-management',
      views: {
        'main@': {
          component: AccountManagementPageContainer,
          $type: 'react',
        },
      },
      data: {
        pageTitleMain: {
          label: 'Account Management',
        },
      },
    };

    stateConfigProvider.addToRootState(accountManagement);
  },
]);
