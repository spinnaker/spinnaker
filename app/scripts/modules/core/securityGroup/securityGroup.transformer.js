'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.securityGroup.transformer.service', [
  require('../cloudProvider/serviceDelegate.service.js'),
])
  .factory('securityGroupTransformer', function (serviceDelegate) {

    function normalizeSecurityGroup(securityGroup) {
      serviceDelegate.getDelegate(securityGroup.provider || securityGroup.type, 'securityGroup.transformer').
        normalizeSecurityGroup(securityGroup);
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };

  });
