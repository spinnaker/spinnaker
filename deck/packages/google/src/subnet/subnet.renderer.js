'use strict';

import _ from 'lodash';

import { NetworkReader } from '@spinnaker/core';

export class GceSubnetRenderer {
  constructor() {
    this.gceNetworks = undefined;

    NetworkReader.listNetworksByProvider('gce').then((networks) => {
      this.gceNetworks = networks;
    });
  }

  render(serverGroup) {
    if (serverGroup.subnet) {
      return serverGroup.subnet;
    } else {
      const autoCreateSubnets = _.chain(this.gceNetworks)
        .filter({ account: serverGroup.account, name: serverGroup.network })
        .map('autoCreateSubnets')
        .head()
        .value();

      return autoCreateSubnets ? '(Auto-select)' : '[none]';
    }
  }
}
