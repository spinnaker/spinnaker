'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { NetworkReader } from '@spinnaker/core';

export const ORACLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER = 'spinnaker.oracle.securityGroup.transformer';
export const name = ORACLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
module(ORACLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER, []).factory('oracleSecurityGroupTransformer', function () {
  const provider = 'oracle';

  function normalizeSecurityGroup(securityGroup) {
    return NetworkReader.listNetworksByProvider(provider).then(_.partial(addVcnNameToSecurityGroup, securityGroup));
  }

  function addVcnNameToSecurityGroup(securityGroup, vcns) {
    const matches = vcns.find((vcn) => vcn.id === securityGroup.network);
    securityGroup.vpcName = matches.length ? matches[0].name : '';
  }

  return {
    normalizeSecurityGroup: normalizeSecurityGroup,
  };
});
