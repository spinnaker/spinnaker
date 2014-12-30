'use strict';

angular
  .module('deckApp.loadBalancer.write.service', [])
  .factory('loadBalancerWriter', function(infrastructureCaches, scheduledCache, taskExecutor) {

    function deleteLoadBalancer(loadBalancer, applicationName) {
      infrastructureCaches.loadBalancers.removeAll();
      return taskExecutor.executeTask({
        job: [
          {
            type: 'deleteLoadBalancer',
            loadBalancerName: loadBalancer.name,
            regions: [loadBalancer.region],
            credentials: loadBalancer.accountId,
            providerType: loadBalancer.providerType
          }
        ],
        application: applicationName,
        description: 'Delete load balancer: ' + loadBalancer.name + ' in ' + loadBalancer.accountId + ':' + loadBalancer.region
      });
    }


    function upsertLoadBalancer(loadBalancer, applicationName, descriptor) {
      var name = loadBalancer.clusterName || loadBalancer.name;
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
      scheduledCache.removeAll();
      return taskExecutor.executeTask({
        job: [
          loadBalancer
        ],
        application: applicationName,
        description: descriptor + ' Load Balancer: ' + name
      });
    }

    return {
      deleteLoadBalancer: deleteLoadBalancer,
      upsertLoadBalancer: upsertLoadBalancer
    };

  });