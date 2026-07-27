import { AccountManagementPageContainer } from './AccountManagementPageContainer';
import { registerRootState } from '../../navigation/rootState.registration';
import type { INestedState } from '../../navigation/state.provider';

registerRootState((stateConfigProvider) => {
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
});
