import { ApplicationStateProvider } from './application.state.provider';
import { registerRootState } from '../navigation/rootState.registration';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { Applications } from './search/Applications';

registerRootState((stateConfigProvider: StateConfigProvider) => {
  const applicationStateProvider = new ApplicationStateProvider(stateConfigProvider);
  const applicationsState: INestedState = {
    name: 'applications',
    url: '/applications?create',
    views: {
      'main@': {
        component: Applications,
        $type: 'react',
      },
    },
    data: {
      pageTitleMain: {
        label: 'Applications',
      },
    },
    children: [],
  };

  applicationStateProvider.addParentState(applicationsState, 'main@');
  stateConfigProvider.addToRootState(applicationsState);
});
