'use strict';

const angular = require('angular');
import _ from 'lodash';

import { NETWORK_READ_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.gce.subnet.renderer', [
  NETWORK_READ_SERVICE,
])
  .factory('gceSubnetRenderer', function ($q, networkReader) {

    var gceNetworks;

    networkReader.listNetworksByProvider('gce').then(function(networks) {
      gceNetworks = networks;
    });

    function render(serverGroup) {
      if (serverGroup.subnet) {
        return serverGroup.subnet;
      } else {
        let autoCreateSubnets = _.chain(gceNetworks)
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
