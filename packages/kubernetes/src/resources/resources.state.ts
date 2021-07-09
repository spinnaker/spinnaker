import { module } from 'angular';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, INestedState } from '@spinnaker/core';
import { KubernetesResourceDetails } from './ResourceDetails';

export interface IKubernetesResourceStateParams {
  provider: string;
  accountId: string;
  region: string;
  kubernetesResource: string;
}

export const KUBERNETES_RESOURCE_STATES = 'spinnaker.core.kubernetesResource.states';
module(KUBERNETES_RESOURCE_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    const kubernetesResourceDetails: INestedState = {
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

    applicationStateProvider.addInsightDetailState(kubernetesResourceDetails);
  },
]);
