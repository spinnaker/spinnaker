'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.subnet.renderer', [
  require('../../core/network/network.read.service.js'),
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
