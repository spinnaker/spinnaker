'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.securityGroup.reader', [
])
  .factory('awsSecurityGroupReader', function () {

    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      return indexedSecurityGroups[container.account][container.region][securityGroupId];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
