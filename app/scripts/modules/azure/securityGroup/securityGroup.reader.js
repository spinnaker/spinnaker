'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.securityGroup.reader', [
])
  .factory('azureSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      return indexedSecurityGroups[container.account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
