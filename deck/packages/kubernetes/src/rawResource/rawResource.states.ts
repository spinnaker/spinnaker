import type { INestedState } from '@spinnaker/core';
import { registerApplicationState } from '@spinnaker/core';

import { K8sResources } from './component/K8sResources';
import { K8sResourcesFilters } from './component/K8sResourcesFilters';
import { RawResourceDetails } from './component/group/RawResourceDetails';

export interface IKubernetesRawResourceStateParams {
  account: string;
  name: string;
  region: string;
}

export const KUBERNETES_RAW_RESOURCE_DETAILS_STATE: INestedState = {
  name: 'rawResourceDetails',
  url: '/rawResourceDetails/:account/:region/:name',
  views: {
    'detail@../insight': {
      component: RawResourceDetails,
      $type: 'react',
    },
  },
  resolve: {
    account: ['$stateParams', ($stateParams: IKubernetesRawResourceStateParams) => $stateParams.account],
    name: ['$stateParams', ($stateParams: IKubernetesRawResourceStateParams) => $stateParams.name],
    region: ['$stateParams', ($stateParams: IKubernetesRawResourceStateParams) => $stateParams.region],
  },
  data: {
    pageTitleDetails: {
      title: 'Raw Resource Details',
      nameParam: 'name',
      accountParam: 'account',
    },
    history: {
      type: 'rawResources',
    },
  },
};

export const KUBERNETES_RAW_RESOURCE_INSIGHT_STATE: INestedState = {
  url: '/kubernetes',
  name: 'k8s',
  views: {
    nav: { component: K8sResourcesFilters, $type: 'react' },
    master: { component: K8sResources, $type: 'react' },
  },
  data: {
    pageTitleSection: {
      title: 'Kubernetes',
    },
  },
  children: [],
};

export function registerKubernetesRawResourceStates(): void {
  registerApplicationState((applicationStateProvider) => {
    applicationStateProvider.addInsightState({
      ...KUBERNETES_RAW_RESOURCE_INSIGHT_STATE,
      children: [],
    });
    applicationStateProvider.addInsightDetailState(KUBERNETES_RAW_RESOURCE_DETAILS_STATE);
  });
}
