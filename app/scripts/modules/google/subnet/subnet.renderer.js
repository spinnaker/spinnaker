'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.subnet.renderer', [
  require('../../core/network/network.read.service.js'),
  require('../../core/utils/lodash.js'),
])
  .factory('gceSubnetRenderer', function ($q, networkReader, _) {

    var gceNetworks;

    networkReader.listNetworksByProvider('gce').then(function(networks) {
      gceNetworks = networks;
    });

    function render(serverGroup) {
      if (serverGroup.subnet) {
        return serverGroup.subnet;
      } else {
        let autoCreateSubnets = _(gceNetworks)
          .filter({ account: serverGroup.account, name: serverGroup.network })
          .pluck('autoCreateSubnets')
          .head();

        return autoCreateSubnets ? '(Auto-select)' : '[none]';
      }
    }

    return {
      render: render,
    };
  });
