'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.aws.subnet.renderer', [
])
  .factory('awsSubnetRenderer', function () {

    function render(serverGroup) {
      return serverGroup.subnetType;
    }

    return {
      render: render,
    };
  });
