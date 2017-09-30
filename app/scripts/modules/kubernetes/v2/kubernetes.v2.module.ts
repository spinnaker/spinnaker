import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from '@spinnaker/core';

import '../logo/kubernetes.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const KUBERNETES_V2_MODULE = 'spinnaker.v2.kubernetes';

module(KUBERNETES_V2_MODULE, [
  CLOUD_PROVIDER_REGISTRY,
]).config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
    cloudProviderRegistryProvider.registerProvider('kubernetes', {
      name: 'Kubernetes',
      providerVersion: 'v2',
      logo: {
        path: require('../logo/kubernetes.icon.svg'),
      },
    });
  });
