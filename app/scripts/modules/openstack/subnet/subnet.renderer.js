'use strict';

const angular = require('angular');

import { SUBNET_READ_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.openstack.subnet.renderer', [
  SUBNET_READ_SERVICE,
])
  .factory('openstackSubnetRenderer', function (subnetReader) {

    var subnets;

    subnetReader.listSubnetsByProvider('openstack').then(function(list) {
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
