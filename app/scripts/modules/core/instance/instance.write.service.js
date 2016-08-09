'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.write.service', [
    require('../task/taskExecutor.js'),
    require('../serverGroup/serverGroup.read.service.js'),
    require('../cloudProvider/serviceDelegate.service.js'),
  ])
  .factory('instanceWriter', function (taskExecutor, serverGroupReader, serviceDelegate) {

    function transform(instanceGroup, job) {
      let hasTransformer = serviceDelegate.hasDelegate(
        instanceGroup.cloudProvider, 'instance.multiInstanceTaskTransformer');
      if (hasTransformer) {
        let transformer = serviceDelegate.getDelegate(
          instanceGroup.cloudProvider, 'instance.multiInstanceTaskTransformer');
        transformer.transform(instanceGroup, job);
      }
    }

    function convertGroupToJob(instanceGroup, type, additionalJobProperties) {
      let job = {
        type: type,
        cloudProvider: instanceGroup.cloudProvider,
        instanceIds: instanceGroup.instanceIds,
        credentials: instanceGroup.account,
        region: instanceGroup.region,
        serverGroupName: instanceGroup.serverGroup,
        asgName: instanceGroup.serverGroup
      };

      _.merge(job, additionalJobProperties);

      transform(instanceGroup, job);

      return job;
    }

    function buildMultiInstanceJob(instanceGroups, type, additionalJobProperties = {}) {
      return instanceGroups
        .filter((instanceGroup) => instanceGroup.instances.length > 0)
        .map((instanceGroup) => convertGroupToJob(instanceGroup, type, additionalJobProperties));
    }

    function buildMultiInstanceDescriptor(jobs, base, suffix) {
      let totalInstances = 0;
      jobs.forEach((job) => totalInstances += job.instanceIds.length);
      let descriptor = base + ' ' + totalInstances + ' instance';
      if (totalInstances > 1) {
        descriptor += 's';
      }
      if (suffix) {
        descriptor += ' ' + suffix;
      }
      return descriptor;
    }

    function executeMultiInstanceTask(instanceGroups, application, type, baseDescriptor, descriptorSuffix, additionalJobProperties = {}) {
      let jobs = buildMultiInstanceJob(instanceGroups, type, additionalJobProperties);
      let descriptor = buildMultiInstanceDescriptor(jobs, baseDescriptor, descriptorSuffix);
      return taskExecutor.executeTask({
        job: jobs,
        application: application,
        description: descriptor,
      });
    }

    function terminateInstances(instanceGroups, application) {
      return executeMultiInstanceTask(instanceGroups, application, 'terminateInstances', 'Terminate');
    }

    function terminateInstance(instance, application, params = {}) {
      params.type = 'terminateInstances';
      params.instanceIds = [instance.instanceId];
      params.region = instance.region;
      params.zone = instance.placement.availabilityZone;
      params.credentials = instance.account;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    function terminateInstancesAndShrinkServerGroups(instanceGroups, application) {
      return executeMultiInstanceTask(instanceGroups, application, 'detachInstances', 'Terminate', 'and shrink server groups', {
        'terminateDetachedInstances': true,
        'decrementDesiredCapacity': true,
      });
    }

    function terminateInstanceAndShrinkServerGroup(instance, application, params = {}) {
      return serverGroupReader.getServerGroup(application.name, instance.account, instance.region, instance.serverGroup).
        then(function(serverGroup) {
          params.type = 'terminateInstanceAndDecrementServerGroup';
          params.instance = instance.instanceId;
          params.asgName = instance.serverGroup;
          params.serverGroupName = instance.serverGroup;
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

    function rebootInstances(instanceGroups, application) {
      return executeMultiInstanceTask(instanceGroups, application, 'rebootInstances', 'Reboot');
    }

    function rebootInstance(instance, application, params = {}) {
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

    function deregisterInstancesFromLoadBalancer(instanceGroups, application, loadBalancerNames) {
      let jobs = buildMultiInstanceJob(instanceGroups, 'deregisterInstancesFromLoadBalancer');
      jobs.forEach((job) => job.loadBalancerNames = loadBalancerNames);
      let descriptor = buildMultiInstanceDescriptor(jobs, 'Deregister', 'from ' + loadBalancerNames.join(' and '));
      return taskExecutor.executeTask({
        job: jobs,
        application: application,
        description: descriptor,
      });
    }

    function deregisterInstanceFromLoadBalancer(instance, application, params = {}) {
      params.type = 'deregisterInstancesFromLoadBalancer';
      params.instanceIds = [instance.instanceId];
      params.loadBalancerNames = instance.loadBalancers;
      params.region = instance.region;
      params.credentials = instance.account;
      params.cloudProvider = instance.providerType;
      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Deregister instance: ' + instance.instanceId
      });
    }

    function registerInstancesWithLoadBalancer(instanceGroups, application, loadBalancerNames) {
      let jobs = buildMultiInstanceJob(instanceGroups, 'registerInstancesWithLoadBalancer');
      jobs.forEach((job) => job.loadBalancerNames = loadBalancerNames);
      let descriptor = buildMultiInstanceDescriptor(jobs, 'Register', 'with ' + loadBalancerNames.join(' and '));
      return taskExecutor.executeTask({
        job: jobs,
        application: application,
        description: descriptor,
      });
    }

    function registerInstanceWithLoadBalancer(instance, application, params = {}) {
      params.type = 'registerInstancesWithLoadBalancer';
      params.instanceIds = [instance.instanceId];
      params.loadBalancerNames = instance.loadBalancers;
      params.region = instance.region;
      params.credentials = instance.account;
      params.cloudProvider = instance.providerType;
      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Register instance: ' + instance.instanceId
      });
    }

    function enableInstancesInDiscovery(instanceGroups, application) {
      return executeMultiInstanceTask(instanceGroups, application, 'enableInstancesInDiscovery', 'Enable', 'in discovery');
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
            cloudProvider: instance.provider,
            asgName: instance.serverGroup
          }
        ],
        application: application,
        description: 'Enable instance: ' + instance.instanceId
      });
    }

    function disableInstancesInDiscovery(instanceGroups, application) {
      return executeMultiInstanceTask(instanceGroups, application, 'disableInstancesInDiscovery', 'Disable', 'in discovery');
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
            cloudProvider: instance.provider,
            asgName: instance.serverGroup
          }
        ],
        application: application,
        description: 'Disable instance: ' + instance.instanceId
      });
    }

    return {
      terminateInstance: terminateInstance,
      terminateInstances: terminateInstances,
      rebootInstance: rebootInstance,
      rebootInstances: rebootInstances,
      registerInstanceWithLoadBalancer: registerInstanceWithLoadBalancer,
      registerInstancesWithLoadBalancer: registerInstancesWithLoadBalancer,
      deregisterInstanceFromLoadBalancer: deregisterInstanceFromLoadBalancer,
      deregisterInstancesFromLoadBalancer: deregisterInstancesFromLoadBalancer,
      enableInstanceInDiscovery: enableInstanceInDiscovery,
      enableInstancesInDiscovery: enableInstancesInDiscovery,
      disableInstanceInDiscovery: disableInstanceInDiscovery,
      disableInstancesInDiscovery: disableInstancesInDiscovery,
      terminateInstanceAndShrinkServerGroup: terminateInstanceAndShrinkServerGroup,
      terminateInstancesAndShrinkServerGroups: terminateInstancesAndShrinkServerGroups,
    };

  });
