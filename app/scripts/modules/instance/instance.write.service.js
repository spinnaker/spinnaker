'use strict';

angular
  .module('deckApp.instance.write.service', [
    'deckApp.taskExecutor.service'
  ])
  .factory('instanceWriter', function (taskExecutor) {

    function terminateInstance(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'terminateInstances',
            instanceIds: [instance.instanceId],
            serverGroup: instance.serverGroup,
            launchTimes: [instance.launchTime],
            region: instance.region,
            zone: instance.placement.availabilityZone,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    function rebootInstance(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'rebootInstances',
            instanceIds: [instance.instanceId],
            region: instance.region,
            zone: instance.placement.availabilityZone,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Reboot instance: ' + instance.instanceId
      });
    }

    function deregisterInstanceFromLoadBalancer(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'deregisterInstancesFromLoadBalancer',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Deregister instance: ' + instance.instanceId
      });
    }

    function registerInstanceWithLoadBalancer(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'registerInstancesWithLoadBalancer',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Register instance: ' + instance.instanceId
      });
    }

    function enableInstanceInDiscovery(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'enableInstancesInDiscovery',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Enable instance: ' + instance.instanceId
      });
    }

    function disableInstanceInDiscovery(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'disableInstancesInDiscovery',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Disable instance: ' + instance.instanceId
      });
    }

    return {
      terminateInstance: terminateInstance,
      rebootInstance: rebootInstance,
      registerInstanceWithLoadBalancer: registerInstanceWithLoadBalancer,
      deregisterInstanceFromLoadBalancer: deregisterInstanceFromLoadBalancer,
      enableInstanceInDiscovery: enableInstanceInDiscovery,
      disableInstanceInDiscovery: disableInstanceInDiscovery
    };

  });
