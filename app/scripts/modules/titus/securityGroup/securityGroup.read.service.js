'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titus.securityGroup.reader', [
])
  .factory('titusSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      let account = container.account.replace('titus', '').replace('vpc', '');
      return indexedSecurityGroups[account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
