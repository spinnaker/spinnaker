'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.write.service', [
    require('../task/taskExecutor.js'),
    require('../cache/infrastructureCaches.js'),
  ])
  .factory('loadBalancerWriter', function(infrastructureCaches, taskExecutor) {

    function deleteLoadBalancer(loadBalancer, application, params = {}) {
      params.type = 'deleteLoadBalancer';
      params.loadBalancerName = loadBalancer.name;
      params.regions = [loadBalancer.region];
      params.credentials = loadBalancer.accountId;
      params.cloudProvider = loadBalancer.providerType;

      var operation = taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Delete load balancer: ' + loadBalancer.name
      });

      infrastructureCaches.clearCache('loadBalancers');

      return operation;
    }


    function upsertLoadBalancer(loadBalancer, application, descriptor, params = {}) {
      var name = loadBalancer.clusterName || loadBalancer.name;
      loadBalancer.cloudProvider = loadBalancer.provider;
      let protocol = loadBalancer.healthCheckProtocol || '';
      if (protocol.startsWith('HTTP')) {
        loadBalancer.healthCheck = `${protocol}:${loadBalancer.healthCheckPort}${loadBalancer.healthCheckPath}`;
      } else {
        loadBalancer.healthCheck = `${protocol}:${loadBalancer.healthCheckPort}`;
      }
      loadBalancer.type = 'upsertLoadBalancer';
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
        loadBalancer.securityGroups = null;
      }

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

      return operation;
    }

    return {
      deleteLoadBalancer: deleteLoadBalancer,
      upsertLoadBalancer: upsertLoadBalancer
    };

  });
