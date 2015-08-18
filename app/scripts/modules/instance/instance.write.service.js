'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.instance.write.service', [
    require('../../services/taskExecutor.js'),
    require('../serverGroups/serverGroup.read.service.js'),
  ])
  .factory('instanceWriter', function (taskExecutor, serverGroupReader) {

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

    function terminateInstanceAndShrinkServerGroup(instance, application) {
      return serverGroupReader.getServerGroup(application.name, instance.account, instance.region, instance.serverGroup).
        then(function(serverGroup) {
          var setMaxToNewDesired = serverGroup.asg.minSize === serverGroup.asg.maxSize;
          return taskExecutor.executeTask({
            job: [
              {
                type: 'terminateInstanceAndDecrementAsg',
                instance: instance.instanceId,
                asgName: instance.serverGroup,
                region: instance.region,
                credentials: instance.account,
                providerType: instance.providerType,
                adjustMinIfNecessary: true,
                setMaxToNewDesired: setMaxToNewDesired,
              }
            ],
            application: application,
            description: 'Terminate instance ' + instance.instanceId + ' and shrink ' + instance.serverGroup,
          });
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
            loadBalancerNames: instance.loadBalancers,
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
            loadBalancerNames: instance.loadBalancers,
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
      disableInstanceInDiscovery: disableInstanceInDiscovery,
      terminateInstanceAndShrinkServerGroup: terminateInstanceAndShrinkServerGroup,
    };

  }).name;
