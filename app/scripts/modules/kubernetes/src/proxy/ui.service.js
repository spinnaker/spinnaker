'use strict';

const angular = require('angular');

import {KubernetesProviderSettings} from '../kubernetes.settings';

module.exports = angular.module('spinnaker.proxy.kubernetes.ui.service', [])
  .factory('kubernetesProxyUiService', function($interpolate) {
    let apiPrefix = 'api/v1/proxy/namespaces/kube-system/services/kubernetes-dashboard/#';

    function getHost(accountName) {
      let host = KubernetesProviderSettings.defaults.proxy;
      let account = KubernetesProviderSettings[accountName];

      if (account && account.proxy) {
        host = account.proxy;
      }

      return host;
    }

    function buildLink(accountName, kind, namespace, serverGroupName) {
      let host = getHost(accountName);
      if (!host.startsWith('http://') && !host.startsWith('https://')) {
        host = 'http://' + host;
      }
      return host + '/' + apiPrefix + '/' + kind.toLowerCase() + '/' + namespace + '/' + serverGroupName;
    }

    function getInstanceLink(accountName, instance) {
      const template = KubernetesProviderSettings.defaults.instanceLinkTemplate;
      return $interpolate(template)(Object.assign({host: getHost(accountName)}, instance));
    }

    return {
      buildLink: buildLink,
      getInstanceLink: getInstanceLink,
    };
  });
