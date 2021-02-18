import { StateParams } from '@uirouter/angularjs';
import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, INestedState } from '@spinnaker/core';

import { EcsTargetGroupDetails } from '../loadBalancer/details/targetGroupDetails';
//import { IEcsTargetGroup } from '../domain/IEcsLoadBalancer';

export const ECS_TARGET_GROUP_STATES = 'spinnaker.ecs.loadBalancer.targetGroup.states';
module(ECS_TARGET_GROUP_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    const ecsTargetGroupDetails: INestedState = {
      name: 'ecsTargetGroupDetails',
      url: '/ecsTargetGroupDetails/:provider/:accountId/:region/:vpcId/:loadBalancerName/:name',
      params: {
        vpcId: {
          value: null,
          squash: true,
        },
      },
      views: {
        'detail@../insight': {
          component: EcsTargetGroupDetails,
          $type: 'react',
        },
      },
      resolve: {
        accountId: ['$stateParams', ($stateParams: StateParams) => $stateParams.accountId],
        name: ['$stateParams', ($stateParams: StateParams) => $stateParams.name],
        provider: ['$stateParams', ($stateParams: StateParams) => $stateParams.provider],
        // resolve flat params into an IEcsTargetGroup
        targetGroup: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return {
              loadBalancerName: $stateParams.loadBalancerName,
              targetGroupName: $stateParams.name,
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

    applicationStateProvider.addInsightDetailState(ecsTargetGroupDetails);
  },
]);
