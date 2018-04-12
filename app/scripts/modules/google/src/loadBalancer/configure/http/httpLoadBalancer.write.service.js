'use strict';

const angular = require('angular');

import { InfrastructureCaches, TASK_EXECUTOR } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.deck.gce.httpLoadBalancer.write.service', [TASK_EXECUTOR])
  .factory('gceHttpLoadBalancerWriter', function(taskExecutor) {
    function upsertLoadBalancers(loadBalancers, application, descriptor) {
      loadBalancers.forEach(lb => {
        angular.extend(lb, {
          type: 'upsertLoadBalancer',
          cloudProvider: 'gce',
          loadBalancerName: lb.name,
        });
      });

      InfrastructureCaches.clearCache('loadBalancers');
      InfrastructureCaches.clearCache('backendServices');
      InfrastructureCaches.clearCache('healthChecks');

      return taskExecutor.executeTask({
        job: loadBalancers,
        application: application,
        description: `${descriptor} Load Balancer: ${loadBalancers[0].urlMapName}`,
      });
    }

    function deleteLoadBalancers(loadBalancer, application, params = {}) {
      let job = {
        type: 'deleteLoadBalancer',
        loadBalancerName: loadBalancer.listeners[0].name,
        regions: ['global'],
        region: 'global',
        loadBalancerType: 'HTTP',
        cloudProvider: loadBalancer.provider,
        credentials: loadBalancer.account,
      };

      angular.extend(job, params);

      InfrastructureCaches.clearCache('loadBalancers');
      InfrastructureCaches.clearCache('backendServices');
      InfrastructureCaches.clearCache('healthChecks');

      return taskExecutor.executeTask({
        job: [job],
        application: application,
        description: `Delete load balancer: ${loadBalancer.urlMapName} in ${loadBalancer.account}:global`,
      });
    }

    return { upsertLoadBalancers, deleteLoadBalancers };
  });
