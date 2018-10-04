'use strict';

const angular = require('angular');
import _ from 'lodash';

import { NetworkReader } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.subnet.renderer', []).factory('gceSubnetRenderer', function() {
  let gceNetworks;

  NetworkReader.listNetworksByProvider('gce').then(function(networks) {
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
