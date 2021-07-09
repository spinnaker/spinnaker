import { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { LoadBalancerDetails } from './LoadBalancerDetails';
import { LoadBalancers } from './LoadBalancers';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider } from '../application';
import { filterModelConfig } from './filter/LoadBalancerFilterModel';
import { LoadBalancerFilters } from './filter/LoadBalancerFilters';
import { INestedState, StateConfigProvider } from '../navigation';

export const LOAD_BALANCER_STATES = 'spinnaker.core.loadBalancer.states';
module(LOAD_BALANCER_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  'stateConfigProvider',
  (applicationStateProvider: ApplicationStateProvider, stateConfigProvider: StateConfigProvider) => {
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
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
        loadBalancer: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId,
            };
          },
        ],
      },
      data: {
        pageTitleDetails: {
          title: 'Load Balancer Details',
          nameParam: 'name',
          accountParam: 'accountId',
          regionParam: 'region',
        },
        history: {
          type: 'loadBalancers',
        },
      },
    };

    const loadBalancers: INestedState = {
      url: `/loadBalancers?${stateConfigProvider.paramsToQuery(filterModelConfig)}`,
      name: 'loadBalancers',
      views: {
        nav: { component: LoadBalancerFilters, $type: 'react' },
        master: { component: LoadBalancers, $type: 'react' },
      },
      params: stateConfigProvider.buildDynamicParams(filterModelConfig),
      data: {
        pageTitleSection: {
          title: 'Load Balancers',
        },
      },
      children: [],
    };

    applicationStateProvider.addInsightState(loadBalancers);
    applicationStateProvider.addInsightDetailState(loadBalancerDetails);
  },
]);
