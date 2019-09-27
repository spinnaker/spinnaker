'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.configure.loadBalancers.controller', [
    require('../configuration.service').name,
  ])
  .controller('kubernetesServerGroupLoadBalancersController', ['kubernetesServerGroupConfigurationService', '$scope']);
