'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titus.securityGroup.reader', [
])
  .factory('titusSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      // TODO: this is bad, but this method is not async and making it async is going to be non-trivial
      let account = container.account.replace('titus', '').replace('vpc', '').replace('dev', 'test');
      return indexedSecurityGroups[account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
