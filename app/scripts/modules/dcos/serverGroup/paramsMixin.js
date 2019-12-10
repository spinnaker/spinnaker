'use strict';

const angular = require('angular');

export const DCOS_SERVERGROUP_PARAMSMIXIN = 'spinnaker.dcos.serverGroup.paramsMixin';
export const name = DCOS_SERVERGROUP_PARAMSMIXIN; // for backwards compatibility
angular.module(DCOS_SERVERGROUP_PARAMSMIXIN, []).factory('dcosServerGroupParamsMixin', function() {
  function destroyServerGroup(serverGroup) {
    return {
      dcosCluster: serverGroup.dcosCluster,
      group: serverGroup.group,
      interestingHealthProviderNames: ['DcosService'],
    };
  }

  function enableServerGroup(serverGroup) {
    return {
      dcosCluster: serverGroup.dcosCluster,
      group: serverGroup.group,
      interestingHealthProviderNames: ['DcosService'],
    };
  }

  function disableServerGroup(serverGroup) {
    return {
      dcosCluster: serverGroup.dcosCluster,
      group: serverGroup.group,
      interestingHealthProviderNames: ['DcosService'],
    };
  }

  return {
    destroyServerGroup: destroyServerGroup,
    enableServerGroup: enableServerGroup,
    disableServerGroup: disableServerGroup,
  };
});
