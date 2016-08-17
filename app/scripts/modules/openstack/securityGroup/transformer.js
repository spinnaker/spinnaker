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
        description: '',
        detail: '',
        account: settings.providers.openstack ? settings.providers.openstack.defaults.account : null,
        rules: [],
      };
    }

    function constructNewIngressRule() {
      return {
          fromPort: 1,
          toPort: 65535,
          prevcidr: '',
          cidr: '',
          ruleType: 'TCP',
          remoteSecurityGroupId : '',
          icmpType : -1,
          icmpCode : -1
      };
    }

    function prepareForSaving(securityGroup) {
      _.forEach(securityGroup.rules, function(value) {

        if(value['remoteSecurityGroupId'] === 'CIDR') {
          value['remoteSecurityGroupId'] = '';
        }
      });

      return securityGroup;
    }

    function prepareForEdit(securityGroup) {
      securityGroup.rules = _.map(securityGroup.inboundRules, function(sgRule) {
          return {

              fromPort:  sgRule.protocol.toUpperCase() !== 'ICMP' ? sgRule.portRanges[0].startPort : '',
              toPort: sgRule.protocol.toUpperCase() !== 'ICMP' ? sgRule.portRanges[0].endPort : '',

              icmpType:  sgRule.protocol.toUpperCase() === 'ICMP' ? sgRule.portRanges[0].startPort : '',
              icmpCode: sgRule.protocol.toUpperCase() === 'ICMP' ? sgRule.portRanges[0].endPort : '',

              cidr: sgRule.range ? sgRule.range.ip + sgRule.range.cidr : '',
              ruleType: sgRule.protocol.toUpperCase(),
              prevcidr: sgRule.range ? sgRule.range.ip + sgRule.range.cidr : '',
              remoteSecurityGroupId: sgRule.securityGroup ? sgRule.securityGroup.id : 'CIDR'
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
      prepareForSaving: prepareForSaving,
    };
  });
