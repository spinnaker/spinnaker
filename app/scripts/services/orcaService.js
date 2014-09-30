'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('orcaService', function(settings, Restangular, scheduler, notifications, urlBuilder) {

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);
      RestangularConfigurer.setDefaultHeaders( {'Content-Type':'application/context+json'} );
    }).all('ops');

    function executeTask(task) {
      notifications.create({
        title: task.application,
        message: task.description,
        href: urlBuilder.buildFromMetadata({
          type: 'tasks',
          application: task.application,
        }),
      });
      return scheduler.scheduleOnCompletion(endpoint.post(task));
    }

    function destroyServerGroup(serverGroup, applicationName) {
      return executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'destroyAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            user: 'deckUser',
            providerType: serverGroup.type
          }
        ],
        application: applicationName,
        description: 'Destroy Server Group: ' + serverGroup.name
      });
    }

    function disableServerGroup(serverGroup, applicationName) {
      return executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'disableAsg',
            regions: [serverGroup.region],
            credentials: serverGroup.account,
            user: 'deckUser'
          }
        ],
        application: applicationName,
        description: 'Disable Server Group: ' + serverGroup.name
      });
    }

    function enableServerGroup(serverGroup, applicationName) {
      return executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'enableAsg',
            regions: [serverGroup.region],
            credentials: serverGroup.account,
            user: 'deckUser'
          }
        ],
        application: applicationName,
        description: 'Enable Server Group: ' + serverGroup.name
      });
    }

    function resizeServerGroup(serverGroup, capacity, applicationName) {
      return executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'resizeAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            user: 'deckUser',
            capacity: capacity,
            providerType: serverGroup.type
          }
        ],
        application: applicationName,
        description: 'Resize Server Group: ' + serverGroup.name + ' to ' + capacity.min + '/' + capacity.desired + '/' + capacity.max
      });
    }

    function upsertSecurityGroup(securityGroup, applicationName, descriptor) {
      securityGroup.type = 'upsertSecurityGroup';
      securityGroup.user = 'deckUser';
      return executeTask({
        job: [
          securityGroup
        ],
        application: applicationName,
        description: descriptor + ' Security Group: ' + securityGroup.name
      });
    }

    function upsertLoadBalancer(loadBalancer, applicationName, descriptor) {
      var name = loadBalancer.clusterName || loadBalancer.name;
      loadBalancer.type = 'upsertAmazonLoadBalancer';
      loadBalancer.user = 'deckUser';
      loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort + loadBalancer.healthCheckPath;
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones;
      if (!loadBalancer.vpcId) {
        loadBalancer.securityGroups = null;
      }
      return executeTask({
        job: [
          loadBalancer
        ],
        application: applicationName,
        description: descriptor + ' Load Balancer: ' + name
      });
    }

    function deleteLoadBalancer(loadBalancer, applicationName) {
      return executeTask({
        job: [
          {
            type: 'deleteAmazonLoadBalancer',
            loadBalancerName: loadBalancer.name,
            regions: [loadBalancer.region],
            credentials: loadBalancer.accountId,
            user: 'deckUser'
          }
        ],
        application: applicationName,
        description: 'Delete load balancer: ' + loadBalancer.name + ' in ' + loadBalancer.accountId + ':' + loadBalancer.region
      });
    }

    function terminateInstance(instance, applicationName) {
      return executeTask({
        job: [
          {
            type: 'terminateInstances',
            instanceIds: [instance.instanceId],
            region: instance.region,
            zone: instance.placement.availabilityZone,
            credentials: instance.account,
            user: 'deckUser',
            providerType: instance.providerType
          }
        ],
        application: applicationName,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    function cloneServerGroup(command, applicationName, descriptor) {
      command.asgName = command.source.asgName;
      command.type = 'copyLastAsg';
      command.user = 'deckUser';
      return executeTask({
        job: [
          command
        ],
        application: applicationName,
        description: descriptor + ' Server Group: ' + command.source.asgName
      });
    }

    return {
      cloneServerGroup: cloneServerGroup,
      deleteLoadBalancer: deleteLoadBalancer,
      destroyServerGroup: destroyServerGroup,
      disableServerGroup: disableServerGroup,
      enableServerGroup: enableServerGroup,
      resizeServerGroup: resizeServerGroup,
      terminateInstance: terminateInstance,
      upsertSecurityGroup: upsertSecurityGroup,
      upsertLoadBalancer: upsertLoadBalancer
    };
  });
