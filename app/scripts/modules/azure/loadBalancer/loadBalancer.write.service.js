'use strict';

import _ from 'lodash';
let angular = require('angular');
import {TASK_EXECUTOR} from 'core/task/taskExecutor';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular
  .module('spinnaker.azure.loadBalancer.write.service', [
    TASK_EXECUTOR,
    INFRASTRUCTURE_CACHE_SERVICE,
  ])
  .factory('azureLoadBalancerWriter', function(infrastructureCaches, taskExecutor) {


    function upsertLoadBalancer(loadBalancer, application, descriptor, params = {}) {

      // We want to extend params with all attributes from loadBalancer, but only if they don't already exist.
      _.assignWith(params, loadBalancer, function(value, other) {
        return _.isUndefined(value) ? other : value;
      });

      var operation = taskExecutor.executeTask({
        job: [params],
        application: application,
        description: descriptor + ' Load Balancer: ' + name
      });

      infrastructureCaches.clearCache('loadBalancers');
      infrastructureCaches.clearCache('networks');

      return operation;
    }

    function deleteLoadBalancer(loadBalancer, application, params = {}) {
      params.type = 'deleteLoadBalancer';
      params.loadBalancerName = loadBalancer.name;
      params.regions = [loadBalancer.region];
      params.credentials = loadBalancer.accountId;
      params.cloudProvider = loadBalancer.providerType;
      params.appName = application.name;

      var operation = taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Delete load balancer: ' + loadBalancer.name
      });

      infrastructureCaches.clearCache('loadBalancers');

      return operation;
    }

    return {
      deleteLoadBalancer: deleteLoadBalancer,
      upsertLoadBalancer: upsertLoadBalancer
    };

  });
