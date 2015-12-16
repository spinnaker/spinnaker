'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.write.service', [
    require('../task/taskExecutor.js'),
    require('../serverGroup/serverGroup.read.service.js'),
  ])
  .factory('instanceWriter', function (taskExecutor, serverGroupReader) {

    function terminateInstance(instance, application, params={}) {
      params.type = 'terminateInstances';
      params.instanceIds = [instance.instanceId];
      params.serverGroup = instance.serverGroup;
      params.launchTimes = [instance.launchTime];
      params.region = instance.region;
      params.zone = instance.placement.availabilityZone;
      params.credentials = instance.account;
      params.providerType = instance.providerType;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    function terminateInstanceAndShrinkServerGroup(instance, application, params={}) {
      return serverGroupReader.getServerGroup(application.name, instance.account, instance.region, instance.serverGroup).
        then(function(serverGroup) {
          params.type = 'terminateInstanceAndDecrementServerGroup';
          params.instance = instance.instanceId;
          params.asgName = instance.serverGroup;
          params.region = instance.region;
          params.credentials = instance.account;
          params.cloudProvider = instance.provider;
          params.adjustMinIfNecessary = true;
          params.setMaxToNewDesired = serverGroup.asg.minSize === serverGroup.asg.maxSize;

          return taskExecutor.executeTask({
            job: [params],
            application: application,
            description: 'Terminate instance ' + instance.instanceId + ' and shrink ' + instance.serverGroup,
          });
        });
    }

    function rebootInstance(instance, application, params={}) {
      params.type = 'rebootInstances';
      params.instanceIds = [instance.instanceId];
      params.region = instance.region;
      params.zone = instance.placement.availabilityZone;
      params.credentials = instance.account;
      params.cloudProvider = instance.providerType;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Reboot instance: ' + instance.instanceId
      });
    }

    function deregisterInstanceFromLoadBalancer(instance, application, params={}) {
      params.type = 'deregisterInstancesFromLoadBalancer';
      params.instanceIds = [instance.instanceId];
      params.loadBalancerNames = instance.loadBalancers;
      params.region = instance.region;
      params.credentials = instance.account;
      params.providerType = instance.providerType;
      params.cloudProvider = instance.providerType;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Deregister instance: ' + instance.instanceId
      });
    }

    function registerInstanceWithLoadBalancer(instance, application, params={}) {
      params.type = 'registerInstancesWithLoadBalancer';
      params.instanceIds = [instance.instanceId];
      params.loadBalancerNames = instance.loadBalancers;
      params.region = instance.region;
      params.credentials = instance.account;
      params.providerType = instance.providerType;
      params.cloudProvider = instance.providerType;

      return taskExecutor.executeTask({
        job: [params],
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
            providerType: instance.providerType,
            cloudProvider: instance.providerType,
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
            providerType: instance.providerType,
            cloudProvider: instance.providerType,
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

  });
