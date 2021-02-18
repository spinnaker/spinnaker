import { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, INestedState } from '@spinnaker/core';

import { TargetGroupDetails } from './TargetGroupDetails';

export const TARGET_GROUP_STATES = 'spinnaker.amazon.loadBalancer.targetGroup.states';
module(TARGET_GROUP_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
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
        targetGroup: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              loadBalancerName: $stateParams.loadBalancerName,
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
          title: 'Target Group Details',
          nameParam: 'name',
          accountParam: 'accountId',
          regionParam: 'region',
        },
      },
    };

    applicationStateProvider.addInsightDetailState(targetGroupDetails);
  },
]);
