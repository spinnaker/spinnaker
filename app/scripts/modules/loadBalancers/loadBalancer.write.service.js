'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer.write.service', [
    require('../../services/taskExecutor.js'),
    require('../caches/infrastructureCaches.js'),
    require('../caches/scheduledCache.js')
  ])
  .factory('loadBalancerWriter', function(infrastructureCaches, taskExecutor) {

    function deleteLoadBalancer(loadBalancer, application) {
      var operation = taskExecutor.executeTask({
        job: [
          {
            type: 'deleteLoadBalancer',
            loadBalancerName: loadBalancer.name,
            regions: [loadBalancer.region],
            credentials: loadBalancer.accountId,
            providerType: loadBalancer.providerType,
            vpcId: loadBalancer.vpcId || '',
          }
        ],
        application: application,
        description: 'Delete load balancer: ' + loadBalancer.name + ' in ' + loadBalancer.accountId + ':' + loadBalancer.region
      });

      infrastructureCaches.clearCache('loadBalancers');

      return operation;
    }


    function upsertLoadBalancer(loadBalancer, application, descriptor) {
      var name = loadBalancer.clusterName || loadBalancer.name;
      loadBalancer.providerType = loadBalancer.provider;
      if (loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0) {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort + loadBalancer.healthCheckPath;
      } else {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort;
      }
      loadBalancer.type = 'upsertAmazonLoadBalancer';
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
        loadBalancer.securityGroups = null;
      }
      var operation = taskExecutor.executeTask({
        job: [
          loadBalancer
        ],
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

  }).name;
