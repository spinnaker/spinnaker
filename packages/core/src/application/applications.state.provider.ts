import { module } from 'angular';

import type { ApplicationStateProvider } from './application.state.provider';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import type { INestedState, StateConfigProvider } from '../navigation/state.provider';
import { STATE_CONFIG_PROVIDER } from '../navigation/state.provider';
import { Applications } from './search/Applications';

export const APPLICATIONS_STATE_PROVIDER = 'spinnaker.core.application.applications.state';
module(APPLICATIONS_STATE_PROVIDER, [STATE_CONFIG_PROVIDER, APPLICATION_STATE_PROVIDER]).config([
  'stateConfigProvider',
  'applicationStateProvider',
  (stateConfigProvider: StateConfigProvider, applicationStateProvider: ApplicationStateProvider) => {
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
  },
]);
