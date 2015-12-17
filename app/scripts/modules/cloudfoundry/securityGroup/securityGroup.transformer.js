'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.securityGroup.transformer', [
])
  .factory('cfSecurityGroupTransformer', function ($q) {

    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup); // no-op
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };

  });
