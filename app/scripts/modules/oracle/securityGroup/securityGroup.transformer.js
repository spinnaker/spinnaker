'use strict';

const angular = require('angular');

import { NetworkReader } from '@spinnaker/core';

import _ from 'lodash';

module.exports = angular
  .module('spinnaker.oracle.securityGroup.transformer', [])
  .factory('oracleSecurityGroupTransformer', function() {
    const provider = 'oracle';

    function normalizeSecurityGroup(securityGroup) {
      return NetworkReader.listNetworksByProvider(provider).then(_.partial(addVcnNameToSecurityGroup, securityGroup));
    }

    function addVcnNameToSecurityGroup(securityGroup, vcns) {
      const matches = vcns.find(vcn => vcn.id === securityGroup.network);
      securityGroup.vpcName = matches.length ? matches[0].name : '';
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };
  });
