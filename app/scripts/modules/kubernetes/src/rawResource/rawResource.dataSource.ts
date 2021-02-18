import { IQService, module } from 'angular';

import { Application, ApplicationDataSourceRegistry, INFRASTRUCTURE_KEY, REST } from '@spinnaker/core';

export const KUBERNETS_RAW_RESOURCE_DATA_SOURCE = 'spinnaker.core.rawresource.dataSource';
export const KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY = 'k8s';
const KUBERNETS_RAW_RESOURCE_DATA_SOURCE_SREF = `.insight.${KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY}`;

type ApiK8sResource = any;

const fetchK8sResources = (application: Application): PromiseLike<ApiK8sResource> =>
  REST('applications').path(application.name, 'rawResources').get();

const formatK8sResources = ($q: IQService) => (_: Application, result: ApiK8sResource): PromiseLike<ApiK8sResource> =>
  $q.resolve(result);

module(KUBERNETS_RAW_RESOURCE_DATA_SOURCE, []).run([
  '$q',
  ($q: IQService) => {
    ApplicationDataSourceRegistry.registerDataSource({
      key: KUBERNETS_RAW_RESOURCE_DATA_SOURCE_KEY,
      label: 'Kubernetes',
      category: INFRASTRUCTURE_KEY,
      sref: KUBERNETS_RAW_RESOURCE_DATA_SOURCE_SREF,
      primary: true,
      icon: 'fas fa-xs fa-fw fa-th-large',
      iconName: 'spMenuK8s',
      loader: fetchK8sResources,
      onLoad: formatK8sResources($q),
      providerField: 'cloudProvider',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Collections of kubernetes resources',
      defaultData: [],
    });
  },
]);
