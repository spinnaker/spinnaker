'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_SERVERGROUP_PARAMSMIXIN = 'spinnaker.kubernetes.serverGroup.paramsMixin';
export const name = KUBERNETES_V1_SERVERGROUP_PARAMSMIXIN; // for backwards compatibility
module(KUBERNETES_V1_SERVERGROUP_PARAMSMIXIN, []).factory('kubernetesServerGroupParamsMixin', function() {
  function destroyServerGroup(serverGroup) {
    return {
      namespace: serverGroup.region || serverGroup.namespace,
      interestingHealthProviderNames: ['KubernetesService'],
    };
  }

  function enableServerGroup(serverGroup) {
    return {
      namespace: serverGroup.region || serverGroup.namespace,
      interestingHealthProviderNames: ['KubernetesService'],
    };
  }

  function disableServerGroup(serverGroup) {
    return {
      namespace: serverGroup.region || serverGroup.namespace,
      interestingHealthProviderNames: ['KubernetesService'],
    };
  }

  return {
    destroyServerGroup: destroyServerGroup,
    enableServerGroup: enableServerGroup,
    disableServerGroup: disableServerGroup,
  };
});
