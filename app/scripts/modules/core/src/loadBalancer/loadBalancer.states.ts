import { module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import { INestedState, StateConfigProvider } from 'core/navigation/state.provider';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application/application.state.provider';
import { CloudProviderRegistry } from '../cloudProvider/cloudProvider.registry';
import { filterModelConfig } from 'core/loadBalancer/filter/loadBalancerFilter.model';
import { LOAD_BALANCERS_COMPONENT } from 'core/loadBalancer/loadBalancers.component';

export const LOAD_BALANCER_STATES = 'spinnaker.core.loadBalancer.states';
module(LOAD_BALANCER_STATES, [
  APPLICATION_STATE_PROVIDER,
  LOAD_BALANCERS_COMPONENT
]).config((applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {

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
           $stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsTemplateUrl'));
        }],
        controllerProvider: ['$stateParams', 'cloudProviderRegistry',
          ($stateParams: StateParams,
           cloudProviderRegistry: CloudProviderRegistry) => {
            return cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsController');
        }],
        controllerAs: 'ctrl'
      }
    },
    resolve: {
      loadBalancer: ['$stateParams', ($stateParams: StateParams) => {
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
    url: `/loadBalancers?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
    name: 'loadBalancers',
    views: {
      'nav': {
        template: '<load-balancer-filter app="$resolve.app"></load-balancer-filter>',
      },
      'master': {
        template: '<load-balancers app="$resolve.app" style="display: flex;flex: 1 1 auto"></load-balancers>',
      }
    },
    params: stateConfigProvider.buildDynamicParams(filterModelConfig),
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
