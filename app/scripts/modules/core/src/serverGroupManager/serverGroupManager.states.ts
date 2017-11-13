import { ITemplateCacheService, module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application';
import { INestedState } from 'core/navigation';
import { VersionedCloudProviderService } from 'core/cloudProvider';

export interface IServerGroupManagerStateParams {
  provider: string;
  accountId: string;
  region: string;
  serverGroupManager: string;
}

export const SERVER_GROUP_MANAGER_STATES = 'spinnaker.core.serverGroupManager.states';
module(SERVER_GROUP_MANAGER_STATES, [APPLICATION_STATE_PROVIDER])
  .config((applicationStateProvider: ApplicationStateProvider) => {
    const serverGroupManagerDetails: INestedState = {
      name: 'serverGroupManager',
      url: '/serverGroupManagerDetails/:provider/:accountId/:region/:serverGroupManager',
      views: {
        'detail@../insight': {
          templateProvider: ['$templateCache', '$stateParams', 'versionedCloudProviderService',
            ($templateCache: ITemplateCacheService,
             $stateParams: StateParams,
             versionedCloudProviderService: VersionedCloudProviderService) => {
              return $templateCache.get(versionedCloudProviderService.getValue($stateParams.provider, $stateParams.accountId, 'serverGroupManager.detailsTemplateUrl'));
            }],
          controllerProvider: ['$stateParams', 'versionedCloudProviderService',
            ($stateParams: StateParams,
             versionedCloudProviderService: VersionedCloudProviderService) => {
              return versionedCloudProviderService.getValue($stateParams.provider, $stateParams.accountId, 'serverGroupManager.detailsController');
            }],
          controllerAs: 'ctrl'
        }
      },
      resolve: {
        serverGroupManager: ['$stateParams', ($stateParams: IServerGroupManagerStateParams) => $stateParams]
      },
      data: {
        pageTitleDetails: {
          title: 'Server Group Manager Details',
          nameParam: 'serverGroupManager',
          accountParam: 'accountId',
          regionParam: 'region'
        },
        history: {
          type: 'serverGroupManagers',
        },
      }
    };

    applicationStateProvider.addInsightDetailState(serverGroupManagerDetails);
  });
