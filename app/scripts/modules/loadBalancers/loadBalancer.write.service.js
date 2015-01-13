'use strict';

angular
  .module('deckApp.loadBalancer.write.service', [])
  .factory('loadBalancerWriter', function(infrastructureCaches, scheduledCache, taskExecutor) {

    function deleteLoadBalancer(loadBalancer, application) {
      var operation = taskExecutor.executeTask({
        job: [
          {
            type: 'deleteLoadBalancer',
            loadBalancerName: loadBalancer.name,
            regions: [loadBalancer.region],
            credentials: loadBalancer.accountId,
            providerType: loadBalancer.providerType
          }
        ],
        application: application,
        description: 'Delete load balancer: ' + loadBalancer.name + ' in ' + loadBalancer.accountId + ':' + loadBalancer.region
      });

      operation.then(infrastructureCaches.loadBalancers.removeAll);

      return operation;
    }


    function upsertLoadBalancer(loadBalancer, application, descriptor) {
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
      var operation = taskExecutor.executeTask({
        job: [
          loadBalancer
        ],
        application: application,
        description: descriptor + ' Load Balancer: ' + name
      });

      operation.then(infrastructureCaches.loadBalancers.removeAll);

      return operation;
    }

    return {
      deleteLoadBalancer: deleteLoadBalancer,
      upsertLoadBalancer: upsertLoadBalancer
    };

  });
