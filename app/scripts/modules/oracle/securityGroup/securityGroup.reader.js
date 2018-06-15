'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oracle.securityGroup.reader', [])
  .factory('oracleSecurityGroupReader', function() {
    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      return indexedSecurityGroups[container.account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
