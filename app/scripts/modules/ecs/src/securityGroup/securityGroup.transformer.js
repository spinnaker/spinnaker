'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.securityGroup.transformer', [])
  .factory('ecsSecurityGroupTransformer', function() {
    function normalizeSecurityGroup() {}

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };
  });
