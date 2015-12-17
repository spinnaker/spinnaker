'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.write.service', [
    require('../utils/lodash.js'),
    require('../task/taskExecutor.js'),
    require('../cache/infrastructureCaches.js'),
  ])
  .factory('loadBalancerWriter', function(_, infrastructureCaches, taskExecutor) {

    function deleteLoadBalancer(loadBalancer, application, params={}) {
      params.type = 'deleteLoadBalancer';
      params.loadBalancerName = loadBalancer.name;
      params.regions = [loadBalancer.region];
      params.credentials = loadBalancer.accountId;
      params.cloudProvider = loadBalancer.providerType;

      var operation = taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Delete load balancer: ' + loadBalancer.name + ' in ' + loadBalancer.accountId + ':' + loadBalancer.region
      });

      infrastructureCaches.clearCache('loadBalancers');

      return operation;
    }


    function upsertLoadBalancer(loadBalancer, application, descriptor, params={}) {
      var name = loadBalancer.clusterName || loadBalancer.name;
      loadBalancer.cloudProvider = loadBalancer.provider;
      if (loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0) {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort + loadBalancer.healthCheckPath;
      } else {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort;
      }
      loadBalancer.type = 'upsertLoadBalancer';
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
        loadBalancer.securityGroups = null;
      }

      // We want to extend params with all attributes from loadBalancer, but only if they don't already exist.
      _.assign(params, loadBalancer, function(value, other) {
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
