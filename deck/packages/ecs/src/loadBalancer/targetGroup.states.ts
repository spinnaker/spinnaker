import type { INestedState } from '@spinnaker/core';
import { registerApplicationState } from '@spinnaker/core';

import { EcsTargetGroupDetails } from '../loadBalancer/details/targetGroupDetails';
//import { IEcsTargetGroup } from '../domain/IEcsLoadBalancer';

interface IEcsTargetGroupStateParams {
  accountId: string;
  loadBalancerName: string;
  name: string;
  provider: string;
  region: string;
  vpcId: string;
}

export const ECS_TARGET_GROUP_DETAILS_STATE: INestedState = {
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
    accountId: ['$stateParams', ($stateParams: IEcsTargetGroupStateParams) => $stateParams.accountId],
    name: ['$stateParams', ($stateParams: IEcsTargetGroupStateParams) => $stateParams.name],
    provider: ['$stateParams', ($stateParams: IEcsTargetGroupStateParams) => $stateParams.provider],
    // resolve flat params into an IEcsTargetGroup
    targetGroup: [
      '$stateParams',
      ($stateParams: IEcsTargetGroupStateParams) => {
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

registerApplicationState((applicationStateProvider) => {
  applicationStateProvider.addInsightDetailState(ECS_TARGET_GROUP_DETAILS_STATE);
});
