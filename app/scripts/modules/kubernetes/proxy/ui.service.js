'use strict';

let angular = require('angular');

import {KubernetesProviderSettings} from '../kubernetes.settings';

module.exports = angular.module('spinnaker.proxy.kubernetes.ui.service', [])
  .factory('kubernetesProxyUiService', function() {
    let apiPrefix = 'api/v1/proxy/namespaces/kube-system/services/kubernetes-dashboard/#';

    function getHost(accountName) {
      let host = KubernetesProviderSettings.defaults.proxy;
      let account = KubernetesProviderSettings[accountName];

      if (account && account.proxy) {
        host = account.proxy;
      }

      if (!host.startsWith('http://') && !host.startsWith('https://')) {
        host = 'http://' + host;
      }

      return host;
    }

    function buildLink(accountName, kind, namespace, serverGroupName) {
      let host = getHost(accountName);
      return host + '/' + apiPrefix + '/' + kind.toLowerCase() + '/' + namespace + '/' + serverGroupName;
    }

    return {
      buildLink: buildLink,
    };
  });
