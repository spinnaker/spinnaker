'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.loadBalancer.transformer', [
])
  .factory('kubernetesLoadBalancerTransformer', function (settings) {
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

    function constructNewLoadBalancerTemplate() {
      return {
        provider: 'kubernetes',
        stack: '',
        detail: '',
        serviceType: 'ClusterIP',
        account: settings.providers.kubernetes ? settings.providers.kubernetes.defaults.account : null,
        namespace: settings.providers.kubernetes ? settings.providers.kubernetes.defaults.namespace : null,
        ports: [
          {
            protocol: 'TCP',
            port: 80,
            name: 'http',
          },
        ],
        externalIps: [],
        sessionAffinity: 'None',
        clusterIp: '',
        loadBalancerIp: '',
      };
    }

    function convertLoadBalancerForEditing(loadBalancer) {
      return loadBalancer.description;
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      constructNewLoadBalancerTemplate: constructNewLoadBalancerTemplate,
      serverGroupIsInLoadBalancer: serverGroupIsInLoadBalancer,
      convertLoadBalancerForEditing: convertLoadBalancerForEditing
    };
  });
