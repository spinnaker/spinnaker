import type { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { LoadBalancers } from './LoadBalancers';
import type { ApplicationStateProvider } from '../application';
import { registerApplicationState } from '../application';
import { LoadBalancerDetails } from './details';
import { TargetGroupDetails } from './details/TargetGroupDetailsWrapper';
import { filterModelConfig } from './filter/LoadBalancerFilterModel';
import { LoadBalancerFilters } from './filter/LoadBalancerFilters';
import type { INestedState, StateConfigProvider } from '../navigation';

export interface ILoadBalancerStateParams {
  name: string;
  accountId: string;
  region: string;
  vpcId: string;
  provider: string;
}

export const LOAD_BALANCER_STATES = 'spinnaker.core.loadBalancer.states';
module(LOAD_BALANCER_STATES, []);

registerApplicationState(
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
          ($stateParams: StateParams): ILoadBalancerStateParams => {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId,
              provider: $stateParams.provider,
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

    const targetGroupDetails: INestedState = {
      name: 'targetGroupDetails',
      url: '/targetGroupDetails/:provider/:accountId/:region/:vpcId/:loadBalancerName/:name',
      params: {
        vpcId: {
          value: null,
          squash: true,
        },
      },
      views: {
        'detail@../insight': {
          component: TargetGroupDetails,
          $type: 'react',
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
        name: ['$stateParams', ($stateParams: StateParams) => $stateParams.name],
        provider: ['$stateParams', ($stateParams: StateParams) => $stateParams.provider],
        targetGroup: [
          '$stateParams',
          ($stateParams: StateParams) => ({
            accountId: $stateParams.accountId,
            loadBalancerName: $stateParams.loadBalancerName,
            name: $stateParams.name,
            provider: $stateParams.provider,
            region: $stateParams.region,
            vpcId: $stateParams.vpcId,
          }),
        ],
      },
      data: {
        pageTitleDetails: {
          title: 'Target Group Details',
          nameParam: 'name',
          accountParam: 'accountId',
          regionParam: 'region',
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
    applicationStateProvider.addInsightDetailState(targetGroupDetails);
  },
);
