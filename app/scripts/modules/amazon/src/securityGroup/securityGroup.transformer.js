'use strict';

const angular = require('angular');
import { groupBy } from 'lodash';

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

    function compress(securityGroups) {
      const grouped = groupBy(securityGroups, 'vpcId');
      Object.keys(grouped).forEach(vpcId => {
        grouped[vpcId] = grouped[vpcId].map(g => [g.name, g.id]);
      });
      return grouped;
    }

    function decompress(groupedGroups) {
      const flattened = [];
      Object.keys(groupedGroups).forEach(vpcId => {
        groupedGroups[vpcId].forEach(g => {
          flattened.push({ name: g[0], id: g[1], vpcId });
        });
      });
      return flattened;
    }

    return {
      normalizeSecurityGroup,
      compress,
      decompress,
      supportsCompression: true,
    };
  });
