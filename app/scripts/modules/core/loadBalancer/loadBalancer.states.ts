import {module} from 'angular';

import {INestedState} from 'core/navigation/state.provider';
import {
  APPLICATION_STATE_PROVIDER, ApplicationStateProvider,
  IApplicationStateParams
} from 'core/application/application.state.provider';
import {CloudProviderRegistry} from '../cloudProvider/cloudProvider.registry';

export interface ILoadBalancerDetailsStateParams extends IApplicationStateParams {
  provider: string;
  accountId: string;
  region: string;
  vpcId: string;
  name: string;
}

export const LOAD_BALANCER_STATES = 'spinnaker.core.loadBalancer.states';
module(LOAD_BALANCER_STATES, [
  APPLICATION_STATE_PROVIDER
]).config((applicationStateProvider: ApplicationStateProvider) => {

  const loadBalancerDetails: INestedState = {
    name: 'loadBalancerDetails',
    url: '/loadBalancerDetails/:provider/:accountId/:region/:vpcId/:name',
    params: {
      vpcId: {
        value: null,
        squash: true,
      },
    },
    views: {
      'detail@../insight': {
        templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry',
          ($templateCache: ng.ITemplateCacheService,
           $stateParams: ILoadBalancerDetailsStateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: ILoadBalancerDetailsStateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      loadBalancer: ['$stateParams', ($stateParams: ILoadBalancerDetailsStateParams) => {
        return {
          name: $stateParams.name,
          accountId: $stateParams.accountId,
          region: $stateParams.region,
          vpcId: $stateParams.vpcId
        };
      }]
    },
    data: {
      pageTitleDetails: {
        title: 'Load Balancer Details',
        nameParam: 'name',
        accountParam: 'accountId',
        regionParam: 'region'
      },
      history: {
        type: 'loadBalancers',
      },
    }
  };

  const loadBalancers: INestedState = {
      url: '/loadBalancers',
      name: 'loadBalancers',
      views: {
        'nav': {
          templateUrl: require('../loadBalancer/filter/filterNav.html'),
          controller: 'LoadBalancerFilterCtrl',
          controllerAs: 'loadBalancerFilters'
        },
        'master': {
          templateUrl: require('../loadBalancer/all.html'),
          controller: 'AllLoadBalancersCtrl',
          controllerAs: 'ctrl'
        }
      },
      data: {
        pageTitleSection: {
          title: 'Load Balancers'
        }
      },
      children: [],
    };

  applicationStateProvider.addInsightState(loadBalancers);
  applicationStateProvider.addInsightDetailState(loadBalancerDetails);
});
