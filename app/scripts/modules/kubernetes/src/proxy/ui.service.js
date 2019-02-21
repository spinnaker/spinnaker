'use strict';

const angular = require('angular');

import { KubernetesProviderSettings } from '../kubernetes.settings';

module.exports = angular.module('spinnaker.proxy.kubernetes.ui.service', []).factory('kubernetesProxyUiService', [
  '$interpolate',
  function($interpolate) {
    function getHost(accountName) {
      let host = KubernetesProviderSettings.defaults.proxy;
      let account = KubernetesProviderSettings[accountName];

      if (account && account.proxy) {
        host = account.proxy;
      }

      return host;
    }

    function buildLink(accountName, kind, namespace, serverGroupName) {
      let apiPrefix = KubernetesProviderSettings.defaults.apiPrefix;
      let account = KubernetesProviderSettings[accountName];
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
