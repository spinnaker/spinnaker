'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.securityGroup.reader', [
])
  .factory('kubernetesSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      return indexedSecurityGroups[container.account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
