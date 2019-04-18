'use strict';

const angular = require('angular');

import { InfrastructureCaches, TaskExecutor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.deck.gce.httpLoadBalancer.write.service', [])
  .factory('gceHttpLoadBalancerWriter', function() {
    function upsertLoadBalancers(loadBalancers, application, descriptor) {
      loadBalancers.forEach(lb => {
        angular.extend(lb, {
          type: 'upsertLoadBalancer',
          cloudProvider: 'gce',
          loadBalancerName: lb.name,
        });
      });

      InfrastructureCaches.clearCache('backendServices');
      InfrastructureCaches.clearCache('healthChecks');

      return TaskExecutor.executeTask({
        job: loadBalancers,
        application: application,
        description: `${descriptor} Load Balancer: ${loadBalancers[0].urlMapName}`,
      });
    }

    function deleteLoadBalancers(loadBalancer, application, params = {}) {
      const job = {
        type: 'deleteLoadBalancer',
        loadBalancerName: loadBalancer.listeners[0].name,
        regions: ['global'],
        region: 'global',
        loadBalancerType: 'HTTP',
        cloudProvider: loadBalancer.provider,
        credentials: loadBalancer.account,
      };

      angular.extend(job, params);

      InfrastructureCaches.clearCache('backendServices');
      InfrastructureCaches.clearCache('healthChecks');

      return TaskExecutor.executeTask({
        job: [job],
        application: application,
        description: `Delete load balancer: ${loadBalancer.urlMapName} in ${loadBalancer.account}:global`,
      });
    }

    return { upsertLoadBalancers, deleteLoadBalancers };
  });
