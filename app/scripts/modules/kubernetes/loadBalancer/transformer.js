'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.loadBalancer.transformer', [
])
  .factory('kubernetesLoadBalancerTransformer', function () {
    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = [];
      return loadBalancer;
    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
      return serverGroup.type === 'kubernetes' &&
        serverGroup.account === loadBalancer.account &&
        serverGroup.namespace === loadBalancer.namespace &&
        serverGroup.loadBalancers.indexOf(loadBalancer.name) !== -1;
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
    };
  });
