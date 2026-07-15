'use strict';

import _ from 'lodash';

import { NetworkReader } from '@spinnaker/core';

export class OracleSecurityGroupTransformer {
  normalizeSecurityGroup(securityGroup) {
    return NetworkReader.listNetworksByProvider(provider).then(_.partial(addVcnNameToSecurityGroup, securityGroup));
  }
}

const provider = 'oracle';

function addVcnNameToSecurityGroup(securityGroup, vcns) {
  const match = vcns.find((vcn) => vcn.id === securityGroup.network);
  securityGroup.vpcName = match ? match.name : '';
}
