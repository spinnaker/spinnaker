import { module } from 'angular';

import { ServerGroupManagerDetails } from './ServerGroupManagerDetails';
import type { ApplicationStateProvider } from '../application';
import { APPLICATION_STATE_PROVIDER } from '../application';
import type { INestedState } from '../navigation';

export interface IServerGroupManagerStateParams {
  provider: string;
  accountId: string;
  region: string;
  name: string;
}

export const SERVER_GROUP_MANAGER_STATES = 'spinnaker.core.serverGroupManager.states';
module(SERVER_GROUP_MANAGER_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    const serverGroupManagerDetails: INestedState = {
      name: 'serverGroupManager',
      url: '/serverGroupManagerDetails/:provider/:accountId/:region/:name',
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
          nameParam: 'name',
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
