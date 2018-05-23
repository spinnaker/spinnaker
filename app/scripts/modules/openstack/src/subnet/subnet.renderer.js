'use strict';

const angular = require('angular');

import { SubnetReader } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.subnet.renderer', [])
  .factory('openstackSubnetRenderer', function() {
    var subnets;

    SubnetReader.listSubnetsByProvider('openstack').then(function(list) {
      subnets = list;
    });

    function render(serverGroup) {
      var subnetName = _.chain(subnets)
        .filter({ id: serverGroup.serverGroupParameters.subnetId })
        .map('name')
        .value();

      return subnetName ? subnetName[0] : '<Unknown>';
    }

    return {
      render: render,
    };
  });
