import { module } from 'angular';

import { ServerGroupManagerDetails } from './ServerGroupManagerDetails';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application';
import { INestedState } from '../navigation';

export interface IServerGroupManagerStateParams {
  provider: string;
  accountId: string;
  region: string;
  serverGroupManager: string;
}

export const SERVER_GROUP_MANAGER_STATES = 'spinnaker.core.serverGroupManager.states';
module(SERVER_GROUP_MANAGER_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    const serverGroupManagerDetails: INestedState = {
      name: 'serverGroupManager',
      url: '/serverGroupManagerDetails/:provider/:accountId/:region/:serverGroupManager',
      views: {
        'detail@../insight': {
          component: ServerGroupManagerDetails,
          $type: 'react',
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: IServerGroupManagerStateParams) => $stateParams.accountId],
        serverGroupManager: ['$stateParams', ($stateParams: IServerGroupManagerStateParams) => $stateParams],
      },
      data: {
        pageTitleDetails: {
          title: 'Server Group Manager Details',
          nameParam: 'serverGroupManager',
          accountParam: 'accountId',
          regionParam: 'region',
        },
        history: {
          type: 'serverGroupManagers',
        },
      },
    };

    applicationStateProvider.addInsightDetailState(serverGroupManagerDetails);
  },
]);
