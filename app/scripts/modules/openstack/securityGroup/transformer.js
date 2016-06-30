'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.securityGroup.transformer', [
])
  .factory('openstackSecurityGroupTransformer', function (settings, $q) {

    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup);
    }

    function constructNewSecurityGroupTemplate() {
      return {
        provider: 'openstack',
        region: '',
        stack: '',
        detail: '',
        account: settings.providers.openstack ? settings.providers.openstack.defaults.account : null,
        rules: [],
      };
    }

    function constructNewIngressRule() {
      return {
         fromPort: 80,
          toPort: 80,
          cidr: '0.0.0.0/0',
          type: 'tcp'

      };
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
      constructNewSecurityGroupTemplate: constructNewSecurityGroupTemplate,
      constructNewIngressRule: constructNewIngressRule
    };
  });
