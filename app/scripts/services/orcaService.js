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
            credentials: serverGroup.account,
            user: 'deckUser'
          }
        ],
        application: applicationName,
        description: 'Destroying ASG: ' + serverGroup.name
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
        description: 'Disabling ASG: ' + serverGroup.name
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
        description: 'Enabling ASG: ' + serverGroup.name
      });
    }

    function resizeServerGroup(serverGroup, capacity, applicationName) {
      return executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'resizeAsg',
            regions: [serverGroup.region],
            credentials: serverGroup.account,
            user: 'deckUser',
            capacity: capacity
          }
        ],
        application: applicationName,
        description: 'Resizing ASG: ' + serverGroup.name + ' to ' + capacity.min + '/' + capacity.desired + '/' + capacity.max
      });
    }

    function upsertSecurityGroup(securityGroup, applicationName) {
      securityGroup.type = 'upsertSecurityGroup';
      securityGroup.user = 'deckUser';
      return executeTask({
        job: [
          securityGroup
        ],
        application: applicationName,
        description: 'Upserting Security Group: ' + securityGroup.name
      });
    }

    function upsertLoadBalancer(loadBalancer, applicationName) {
      loadBalancer.type = 'upsertAmazonLoadBalancer';
      loadBalancer.user = 'deckUser';
      loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort + loadBalancer.healthCheckPath;
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones;
      if (loadBalancer.securityGroups) {

      }
      return executeTask({
        job: [
          loadBalancer
        ],
        application: applicationName,
        description: 'Upserting Load Balancer: ' + loadBalancer.clusterName
      });
    }

    function terminateInstance(instance, applicationName) {
      return executeTask({
        job: [
          {
            type: 'terminateInstances',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            user: 'deckUser'
          }
        ],
        application: applicationName,
        description: 'Terminating instance: ' + instance.instanceId
      });
    }

    function cloneServerGroup(command, applicationName) {
      command.asgName = command.source.asgName;
      command.type = 'copyLastAsg';
      command.user = 'deckUser';
      return executeTask({
        job: [
          command
        ],
        application: applicationName,
        description: 'Copy ASG: ' + command.source.asgName
      });
    }

    return {
      cloneServerGroup: cloneServerGroup,
      destroyServerGroup: destroyServerGroup,
      disableServerGroup: disableServerGroup,
      enableServerGroup: enableServerGroup,
      resizeServerGroup: resizeServerGroup,
      terminateInstance: terminateInstance,
      upsertSecurityGroup: upsertSecurityGroup,
      upsertLoadBalancer: upsertLoadBalancer
    };
  });
