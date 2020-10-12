'use strict';

import * as angular from 'angular';

import { InfrastructureCaches, TaskExecutor } from '@spinnaker/core';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE =
  'spinnaker.deck.gce.httpLoadBalancer.write.service';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE; // for backwards compatibility
angular
  .module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE, [])
  .factory('gceHttpLoadBalancerWriter', function () {
    function upsertLoadBalancers(loadBalancers, application, descriptor) {
      loadBalancers.forEach((lb) => {
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
        regions: [loadBalancer.region || 'global'],
        region: loadBalancer.region || 'global',
        loadBalancerType: loadBalancer.loadBalancerType,
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
