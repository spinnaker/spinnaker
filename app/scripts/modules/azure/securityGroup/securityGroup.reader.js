'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.securityGroup.reader', [])
  .factory('azureSecurityGroupReader', function() {
    function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
      //hack to get around securityGroupId not matching id in indexedSecurityGroups.
      var temp = securityGroupId.split('/');
      return indexedSecurityGroups[container.account][container.region][temp[temp.length - 1]];
    }

    return {
      resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
    };
  });
