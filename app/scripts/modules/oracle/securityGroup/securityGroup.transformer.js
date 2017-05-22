'use strict';

const angular = require('angular');

import {
  NETWORK_READ_SERVICE
} from '@spinnaker/core';

import _ from 'lodash';

module.exports = angular.module('spinnaker.oraclebmcs.securityGroup.transformer', [
  NETWORK_READ_SERVICE
])
  .factory('oraclebmcsSecurityGroupTransformer', function (networkReader) {

    const provider = 'oraclebmcs';

    function normalizeSecurityGroup(securityGroup) {
      return networkReader.listNetworksByProvider(provider)
        .then(_.partial(addVcnNameToSecurityGroup, securityGroup));
    }

    function addVcnNameToSecurityGroup(securityGroup, vcns) {
      const matches = vcns.find(vcn => vcn.id === securityGroup.network);
      securityGroup.vpcName = matches.length ? matches[0].name : '';
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup
    };
  });
