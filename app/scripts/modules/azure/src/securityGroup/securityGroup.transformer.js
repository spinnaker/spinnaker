'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.securityGroup.transformer', [])
  .factory('azureSecurityGroupTransformer', function() {
    function normalizeSecurityGroup() {}

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };
  });
