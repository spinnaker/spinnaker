'use strict';

const angular = require('angular');

import { INFRASTRUCTURE_CACHE_SERVICE, TASK_EXECUTOR } from '@spinnaker/core';

module.exports = angular.module('spinnaker.deck.gce.multipleListenerLoadBalancer.write.service', [
    TASK_EXECUTOR,
    INFRASTRUCTURE_CACHE_SERVICE
  ])
  .factory('gceMultipleListenerLoadBalancerWriter', function (taskExecutor, infrastructureCaches) {
    function upsertLoadBalancer (loadBalancers, application, descriptor) {
      const loadBalancerType = loadBalancers[0].loadBalancerType;
      let description;
      if (loadBalancerType === 'HTTP') {
        description = `${descriptor} Load Balancer: ${loadBalancers[0].urlMapName}`;
      } else if (loadBalancerType === 'INTERNAL') {
        description = `${descriptor} Load Balancer: ${loadBalancers[0].backendService.name}`;
      }

      loadBalancers.forEach(lb => {
        angular.extend(lb, {
          type: 'upsertLoadBalancer',
          cloudProvider: 'gce',
          loadBalancerName: lb.name
        });
      });

      infrastructureCaches.clearCache('loadBalancers');
      infrastructureCaches.clearCache('backendServices');
      infrastructureCaches.clearCache('healthChecks');

      return taskExecutor.executeTask({
        job: loadBalancers,
        application: application,
        description: description
      });
    }

    function deleteLoadBalancer (loadBalancer, application, params = {}) {
      let description;
      let job;
      if (loadBalancer.loadBalancerType === 'HTTP') {
        description = `Delete load balancer: ${loadBalancer.urlMapName} in ${loadBalancer.account}:global`;
        job = {
          type: 'deleteLoadBalancer',
          loadBalancerName: loadBalancer.listeners[0].name,
          regions: ['global'],
          region: 'global',
          loadBalancerType: 'HTTP',
          cloudProvider: loadBalancer.provider,
          credentials: loadBalancer.account,
        };
      } else if (loadBalancer.loadBalancerType === 'INTERNAL') {
        description = `Delete load balancer: ${loadBalancer.backendService.name} in ${loadBalancer.account}:${loadBalancer.region}`;
        job = {
          type: 'deleteLoadBalancer',
          loadBalancerName: loadBalancer.listeners[0].name,
          regions: [loadBalancer.region],
          region: loadBalancer.region,
          loadBalancerType: 'INTERNAL',
          cloudProvider: loadBalancer.provider,
          credentials: loadBalancer.account,
        };
      }

      angular.extend(job, params);

      infrastructureCaches.clearCache('loadBalancers');
      infrastructureCaches.clearCache('backendServices');
      infrastructureCaches.clearCache('healthChecks');

      return taskExecutor.executeTask({
        job: [job],
        application: application,
        description: description
      });
    }

    return { upsertLoadBalancer, deleteLoadBalancer };
  });
