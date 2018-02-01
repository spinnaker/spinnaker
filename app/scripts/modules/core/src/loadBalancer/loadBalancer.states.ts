import { module } from 'angular';
import { StateParams } from '@uirouter/angularjs';

import { INestedState, StateConfigProvider } from 'core/navigation';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from 'core/application';
import { filterModelConfig } from 'core/loadBalancer/filter/loadBalancerFilter.model';
import { LoadBalancers } from 'core/loadBalancer/LoadBalancers';
import { VERSIONED_CLOUD_PROVIDER_SERVICE } from 'core/cloudProvider';

import { LoadBalancerDetails } from './LoadBalancerDetails';
export const LOAD_BALANCER_STATES = 'spinnaker.core.loadBalancer.states';
module(LOAD_BALANCER_STATES, [
  APPLICATION_STATE_PROVIDER,
  VERSIONED_CLOUD_PROVIDER_SERVICE,
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
        component: LoadBalancerDetails,
        $type: 'react',
      }
    },
    resolve: {
      accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
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
      'master': { component: LoadBalancers, $type: 'react' }
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
