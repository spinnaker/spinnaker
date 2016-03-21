'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.kubernetes.backend', [ ])
  .controller('kubernetesSecurityGroupBackendController', function() {
    this.maxPort = 65535;
  });
