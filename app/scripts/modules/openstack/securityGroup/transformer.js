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
          ruleType: 'tcp'

      };
    }

    function prepareForEdit(securityGroup) {

      securityGroup.rules = _.map(securityGroup.inboundRules, function(x) {
          return {
              fromPort: x.portRanges[0].startPort,
              toPort: x.portRanges[0].endPort,
              cidr: x.range.ip + x.range.cidr,
              ruleType: x.protocol
          };
      }) || [];
      securityGroup.account = securityGroup.accountName;
      securityGroup.accountName = undefined;
      return securityGroup;
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
      constructNewSecurityGroupTemplate: constructNewSecurityGroupTemplate,
      constructNewIngressRule: constructNewIngressRule,
      prepareForEdit: prepareForEdit,
    };
  });
