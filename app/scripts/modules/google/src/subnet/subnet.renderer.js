'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { NetworkReader } from '@spinnaker/core';

export const GOOGLE_SUBNET_SUBNET_RENDERER = 'spinnaker.gce.subnet.renderer';
export const name = GOOGLE_SUBNET_SUBNET_RENDERER; // for backwards compatibility
module(GOOGLE_SUBNET_SUBNET_RENDERER, []).factory('gceSubnetRenderer', function () {
  let gceNetworks;

  NetworkReader.listNetworksByProvider('gce').then(function (networks) {
    gceNetworks = networks;
  });

  function render(serverGroup) {
    if (serverGroup.subnet) {
      return serverGroup.subnet;
    } else {
      const autoCreateSubnets = _.chain(gceNetworks)
        .filter({ account: serverGroup.account, name: serverGroup.network })
        .map('autoCreateSubnets')
        .head()
        .value();

      return autoCreateSubnets ? '(Auto-select)' : '[none]';
    }
  }

  return {
    render: render,
  };
});
