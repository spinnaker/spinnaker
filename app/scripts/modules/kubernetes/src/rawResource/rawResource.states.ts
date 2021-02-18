import { module } from 'angular';

//import { StateParams } from '@uirouter/angularjs';
import { APPLICATION_STATE_PROVIDER, ApplicationStateProvider, INestedState } from '@spinnaker/core';

import { K8sResources } from './component/K8sResources';
import { K8sResourcesFilters } from './component/K8sResourcesFilters';
import { RawResourceDetails } from './component/group/RawResourceDetails';

export interface IKubernetesRawResourceStateParams {
  account: string;
  name: string;
  region: string;
}

export const KUBERNETS_RAW_RESOURCE_STATES = 'spinnaker.kubernetes.rawresource.states';
module(KUBERNETS_RAW_RESOURCE_STATES, [APPLICATION_STATE_PROVIDER]).config([
  'applicationStateProvider',
  (applicationStateProvider: ApplicationStateProvider) => {
    const rawResourceDetails: INestedState = {
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
    const kubernetes: INestedState = {
      url: `/kubernetes`,
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

    applicationStateProvider.addInsightState(kubernetes);
    applicationStateProvider.addInsightDetailState(rawResourceDetails);
  },
]);
