'use strict';

const angular = require('angular');

import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular.module('spinnaker.aws.securityGroup.transformer', [
  VPC_READ_SERVICE,
])
  .factory('awsSecurityGroupTransformer', function (vpcReader) {

    function normalizeSecurityGroup(securityGroup) {
      return vpcReader.listVpcs().then(addVpcNameToSecurityGroup(securityGroup));
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
