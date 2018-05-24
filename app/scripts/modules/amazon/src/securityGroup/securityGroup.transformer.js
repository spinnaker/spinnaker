'use strict';

const angular = require('angular');

import { VpcReader } from '../vpc/VpcReader';

module.exports = angular
  .module('spinnaker.amazon.securityGroup.transformer', [])
  .factory('awsSecurityGroupTransformer', function() {
    function normalizeSecurityGroup(securityGroup) {
      return VpcReader.listVpcs().then(addVpcNameToSecurityGroup(securityGroup));
    }

    function addVpcNameToSecurityGroup(securityGroup) {
      return function(vpcs) {
        var matches = vpcs.filter(function(test) {
          return test.id === securityGroup.vpcId;
        });
        securityGroup.vpcName = matches.length ? matches[0].name : '';
      };
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };
  });
