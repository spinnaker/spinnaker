'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.securityGroup.reader', [
])
  .factory('gceSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      return indexedSecurityGroups[container.account].global[securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
