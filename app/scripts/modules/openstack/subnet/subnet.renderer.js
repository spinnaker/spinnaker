'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.subnet.renderer', [
])
  .factory('openstackSubnetRenderer', function () {

    function render(serverGroup) {
      return serverGroup.subnetType;
    }

    return {
      render: render,
    };
  });
