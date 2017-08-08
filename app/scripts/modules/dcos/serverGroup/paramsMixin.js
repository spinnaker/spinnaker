'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.paramsMixin', [])
  .factory('dcosServerGroupParamsMixin', function () {

    function destroyServerGroup(serverGroup) {
      return {
        dcosCluster: serverGroup.dcosCluster,
        group: serverGroup.group,
        interestingHealthProviderNames: ['DcosService']
      };
    }

    function enableServerGroup(serverGroup) {
      return {
        dcosCluster: serverGroup.dcosCluster,
        group: serverGroup.group,
        interestingHealthProviderNames: ['DcosService']
      };
    }

    function disableServerGroup(serverGroup) {
      return {
        dcosCluster: serverGroup.dcosCluster,
        group: serverGroup.group,
        interestingHealthProviderNames: ['DcosService']
      };
    }

    return {
      destroyServerGroup: destroyServerGroup,
      enableServerGroup: enableServerGroup,
      disableServerGroup: disableServerGroup,
    };
  });
