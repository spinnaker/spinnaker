'use strict';

import _ from 'lodash';

import { KubernetesProviderSettings } from '../kubernetes.settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.loadBalancer.transformer', [])
  .factory('kubernetesLoadBalancerTransformer', ['$q', function($q) {
    function normalizeLoadBalancer(loadBalancer) {
      loadBalancer.provider = loadBalancer.type;
      loadBalancer.instances = [];
      loadBalancer.instanceCounts = buildInstanceCounts(loadBalancer.serverGroups);
      return $q.resolve(loadBalancer);
    }

    function buildInstanceCounts(serverGroups) {
      let instanceCounts = _.chain(serverGroups)
        .map('instances')
        .flatten()
        .reduce(
          (acc, instance) => {
            acc[_.camelCase(instance.health.state)]++;
            return acc;
          },
          {
            up: 0,
            down: 0,
            outOfService: 0,
            succeeded: 0,
            failed: 0,
            unknown: 0,
          },
        )
        .value();

      instanceCounts.outOfService += _.chain(serverGroups)
        .map('detachedInstances')
        .flatten()
        .value().length;

      return instanceCounts;
    }

    function constructNewLoadBalancerTemplate() {
      return {
        provider: 'kubernetes',
        stack: '',
        detail: '',
        serviceType: 'ClusterIP',
        account: KubernetesProviderSettings.defaults.account,
        namespace: KubernetesProviderSettings.defaults.namespace,
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
      convertLoadBalancerForEditing: convertLoadBalancerForEditing,
    };
  }]);
