import {module} from 'angular';

import {StateParams} from 'angular-ui-router';
import {STATE_CONFIG_PROVIDER, INestedState, StateConfigProvider} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
} from 'core/application/application.state.provider';
import {CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {Application} from 'core/application/application.model';
import {filterModelConfig} from 'core/cluster/filter/clusterFilter.model';

export const SERVER_GROUP_STATES = 'spinnaker.core.serverGroup.states';
module(SERVER_GROUP_STATES, [
  APPLICATION_STATE_PROVIDER,
  STATE_CONFIG_PROVIDER,
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
  const clusters: INestedState = {
    name: 'clusters',
    url: `/clusters?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
    views: {
      'nav': {
        template: '<cluster-filter app="$resolve.app"></cluster-filter>',
      },
      'master': {
        templateUrl: require('../cluster/all.html'),
        controller: 'AllClustersCtrl',
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      // prevents flash of filters if fetchOnDemand is enabled; catch any exceptions so the route resolves
      // and deal with the exception in the AllClustersCtrl
      ready: (app: Application) => app.getDataSource('serverGroups').ready().catch(() => null),
    },
    params: stateConfigProvider.buildDynamicParams(filterModelConfig),
    data: {
      pageTitleSection: {
        title: 'Clusters'
      }
    },
    children: [],
  };

  const serverGroupDetails: INestedState = {
    name: 'serverGroup',
    url: '/serverGroupDetails/:provider/:accountId/:region/:serverGroup',
    views: {
      'detail@../insight': {
        templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry',
          ($templateCache: ng.ITemplateCacheService,
           $stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'serverGroup.detailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'serverGroup.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      serverGroup: ['$stateParams', ($stateParams: StateParams) => {
        return {
          name: $stateParams.serverGroup,
          accountId: $stateParams.accountId,
          region: $stateParams.region,
        };
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Server Group Details',
        nameParam: 'serverGroup',
        accountParam: 'accountId',
        regionParam: 'region'
      },
      history: {
        type: 'serverGroups',
      },
    }
  };

  const multipleServerGroups: INestedState = {
    name: 'multipleServerGroups',
    url: '/multipleServerGroups',
    views: {
      'detail@../insight': {
        templateUrl: require('../serverGroup/details/multipleServerGroups.view.html'),
        controller: 'MultipleServerGroupsCtrl',
        controllerAs: 'vm'
      }
    },
    data: {
      pageTitleDetails: {
        title: 'Multiple Server Groups',
      },
    }
  };

  applicationStateProvider.addInsightState(clusters);
  applicationStateProvider.addInsightDetailState(serverGroupDetails);
  applicationStateProvider.addInsightDetailState(multipleServerGroups);

  stateConfigProvider.addRewriteRule('/applications/{application}', '/applications/{application}/clusters');
});
