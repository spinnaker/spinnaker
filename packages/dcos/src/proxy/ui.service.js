'use strict';

import { module } from 'angular';

export const DCOS_PROXY_UI_SERVICE = 'spinnaker.proxy.dcos.ui.service';
export const name = DCOS_PROXY_UI_SERVICE; // for backwards compatibility
module(DCOS_PROXY_UI_SERVICE, []).factory('dcosProxyUiService', function () {
  const apiPrefix = '#';

  function buildLink(host, accountName, region, name, taskName = null) {
    const regionParts = region != null ? region.replace('_', '/').split('/') : [];
    let link = host + '/' + apiPrefix + '/services/overview/';

    if (regionParts.length > 1) {
      link =
        link +
        encodeURIComponent('/' + accountName + '/' + regionParts.slice(1, regionParts.length).join('/') + '/') +
        name;
    } else {
      link = link + encodeURIComponent('/' + accountName + '/') + name;
    }

    if (taskName) {
      link = link + '/tasks/' + taskName;
    }

    return link;
  }

  function buildLoadBalancerLink(host, accountName, name) {
    return host + '/' + apiPrefix + '/services/overview/' + encodeURIComponent('/' + accountName + '/') + name;
  }

  return {
    buildLink: buildLink,
    buildLoadBalancerLink: buildLoadBalancerLink,
  };
});
