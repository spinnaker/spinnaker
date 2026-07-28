import type { INestedState } from '@spinnaker/core';
import { registerApplicationState } from '@spinnaker/core';
import { KubernetesResourceDetails } from './ResourceDetails';

export interface IKubernetesResourceStateParams {
  provider: string;
  accountId: string;
  region: string;
  kubernetesResource: string;
}

export const KUBERNETES_RESOURCE_DETAILS_STATE: INestedState = {
  name: 'kubernetesResource',
  url: '/manifest/:provider/:accountId/:region/:kubernetesResource',
  views: {
    'detail@../insight': {
      component: KubernetesResourceDetails,
      $type: 'react',
    },
  },
  resolve: {
    accountId: ['$stateParams', ($stateParams: IKubernetesResourceStateParams) => $stateParams.accountId],
    kubernetesResource: ['$stateParams', ($stateParams: IKubernetesResourceStateParams) => $stateParams],
  },
  data: {
    pageTitleDetails: {
      title: 'Generic Kubernetes Resource Details',
      nameParam: 'kubernetesResource',
      accountParam: 'accountId',
      regionParam: 'region',
    },
    history: {
      type: 'kubernetesResource',
    },
  },
};

registerApplicationState((applicationStateProvider) => {
  applicationStateProvider.addInsightDetailState(KUBERNETES_RESOURCE_DETAILS_STATE);
});
