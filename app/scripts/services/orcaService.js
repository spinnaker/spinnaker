'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('orcaService', function(settings, Restangular, scheduler, notifications, urlBuilder, pond, $q) {

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
      var op = endpoint.post(task).then(
        function(task) {
          var taskId = task.ref.substring(task.ref.lastIndexOf('/')+1);
          return pond.one('tasks', taskId).get();
        },
        function(response) {
          var error = {
            status: response.status,
            message: response.statusText
          };
          if (response.data && response.data.message) {
            error.log = response.data.message;
          } else {
            error.log = 'Sorry, no more information.';
          }
          return $q.reject(error);
        }
      );
      return scheduler.scheduleOnCompletion(op);
    }

    function createApplication(app) {
      return executeTask({
        job: [
          {
            type: 'createApplication',
            account: app.account,
            application: {
              name: app.name,
              description: app.description,
              email: app.email,
              owner: app.owner,
              type: app.type,
              group: app.group,
              monitorBucketType: app.monitorBucketType,
              pdApiKey: app.pdApiKey,
              updateTs: app.updateTs,
              createTs: app.createTs,
              tags: app.tags
            }
          }
        ],
        application: app.name,
        description: 'Create Application: ' + app.name
      });
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
      securityGroup.credentials = securityGroup.accountName;
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
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
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

    function cloneServerGroup(command, applicationName, description) {
      command.user = 'deckUser';
      return executeTask({
        job: [
          command
        ],
        application: applicationName,
        description: description
      });
    }

    return {
      cloneServerGroup: cloneServerGroup,
      createApplication: createApplication,
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
