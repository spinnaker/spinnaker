'use strict';

import { module } from 'angular';

import { KubernetesProviderSettings } from '../../kubernetes.settings';

export const KUBERNETES_V1_PROXY_UI_SERVICE = 'spinnaker.proxy.kubernetes.ui.service';
export const name = KUBERNETES_V1_PROXY_UI_SERVICE; // for backwards compatibility
module(KUBERNETES_V1_PROXY_UI_SERVICE, []).factory('kubernetesProxyUiService', [
  '$interpolate',
  function($interpolate) {
    function getHost(accountName) {
      let host = KubernetesProviderSettings.defaults.proxy;
      const account = KubernetesProviderSettings[accountName];

      if (account && account.proxy) {
        host = account.proxy;
      }

      return host;
    }

    function buildLink(accountName, kind, namespace, serverGroupName) {
      let apiPrefix = KubernetesProviderSettings.defaults.apiPrefix;
      const account = KubernetesProviderSettings[accountName];
      if (account && account.apiPrefix) {
        apiPrefix = account.apiPrefix;
      }
      if (apiPrefix == null || apiPrefix === '') {
        apiPrefix = 'api/v1/proxy/namespaces/kube-system/services/kubernetes-dashboard/#';
      }

      let host = getHost(accountName);
      if (!host.startsWith('http://') && !host.startsWith('https://')) {
        host = 'http://' + host;
      }
      return host + '/' + apiPrefix + '/' + kind.toLowerCase() + '/' + namespace + '/' + serverGroupName;
    }

    function getInstanceLink(accountName, instance) {
      const template = KubernetesProviderSettings.defaults.instanceLinkTemplate;
      return $interpolate(template)(Object.assign({ host: getHost(accountName) }, instance));
    }

    return {
      buildLink: buildLink,
      getInstanceLink: getInstanceLink,
    };
  },
]);
