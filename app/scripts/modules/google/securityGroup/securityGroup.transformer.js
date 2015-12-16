'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.securityGroup.transformer', [
])
  .factory('gceSecurityGroupTransformer', function ($q) {

    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup); // no-op
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };

  });
