'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.paramsMixin', [])
  .factory('kubernetesServerGroupParamsMixin', function () {

    function destroyServerGroup(serverGroup) {
      return {
        namespace: serverGroup.namespace,
        interestingHealthProviderNames: ['KubernetesService']
      };
    }

    function enableServerGroup(serverGroup) {
      return {
          namespace: serverGroup.region,
          interestingHealthProviderNames: ['KubernetesService']
      };
    }

    function disableServerGroup(serverGroup) {
      return {
          namespace: serverGroup.region,
          interestingHealthProviderNames: ['KubernetesService']
      };
    }

    return {
      destroyServerGroup: destroyServerGroup,
      enableServerGroup: enableServerGroup,
      disableServerGroup: disableServerGroup,
    };
  });
