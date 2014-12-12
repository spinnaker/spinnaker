'use strict';


angular.module('deckApp')
  .factory('orcaService', function(settings, Restangular, scheduler, notifications, urlBuilder, pond, $q, authenticationService, scheduledCache) {

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);
    }).all('applications');

    function getEndpoint(application) {
      return endpoint.all(application).all('tasks');
    }

    function executeTask(taskCommand) {
      if (taskCommand.job[0].providerType === 'aws') {
        delete taskCommand.job[0].providerType;
      }
      var op = getEndpoint(taskCommand.application).post(taskCommand).then(
        function(task) {
          var taskId = task.ref.substring(task.ref.lastIndexOf('/')+1);
          notifications.create({
            title: taskCommand.application,
            message: taskCommand.description,
            href: urlBuilder.buildFromMetadata({
              type: 'task',
              application: taskCommand.application,
              taskId: taskId
            })
          });
          return pond.one('applications', taskCommand.application).one('tasks', taskId).get();
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
            user: authenticationService.getAuthenticatedUser().name,
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
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            user: authenticationService.getAuthenticatedUser().name,
            providerType: serverGroup.type
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
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            user: authenticationService.getAuthenticatedUser().name,
            providerType: serverGroup.type
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
            user: authenticationService.getAuthenticatedUser().name,
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
      scheduledCache.removeAll();
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
      if (loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0) {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort + loadBalancer.healthCheckPath;
      } else {
        loadBalancer.healthCheck = loadBalancer.healthCheckProtocol + ':' + loadBalancer.healthCheckPort;
      }
      loadBalancer.type = 'upsertAmazonLoadBalancer';
      loadBalancer.user = 'deckUser';
      loadBalancer.availabilityZones = {};
      loadBalancer.availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
        loadBalancer.securityGroups = null;
      }
      scheduledCache.removeAll();
      return executeTask({
        job: [
          loadBalancer
        ],
        application: applicationName,
        description: descriptor + ' Load Balancer: ' + name
      });
    }

    function deleteLoadBalancer(loadBalancer, applicationName) {
      scheduledCache.removeAll();
      return executeTask({
        job: [
          {
            type: 'deleteAmazonLoadBalancer',
            loadBalancerName: loadBalancer.name,
            regions: [loadBalancer.region],
            credentials: loadBalancer.accountId,
            user: authenticationService.getAuthenticatedUser().name
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
            user: authenticationService.getAuthenticatedUser().name,
            providerType: instance.providerType
          }
        ],
        application: applicationName,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.useAllImageSelection) {
        command.amiName = base.viewState.allImageSelection;
      }
      command.availabilityZones = {};
      command.availabilityZones[command.region] = base.availabilityZones;
      if (!command.ramdiskId) {
        delete command.ramdiskId; // TODO: clean up in kato? - should ignore if empty string
      }
      delete command.region;
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.instanceProfile;
      delete command.vpcId;
      delete command.usePreferredZones;

      if (!command.subnetType) {
        delete command.subnetType;
      }
      return command;
    }

    function cloneServerGroup(command, applicationName) {
      command.user = authenticationService.getAuthenticatedUser().name;

      var description;
      if (command.viewState.mode === 'clone') {
        description = 'Create Cloned Server Group from ' + command.source.asgName;
        command.type = 'copyLastAsg';
      } else {
        command.type = 'deploy';
        var asgName = applicationName;
        if (command.stack) {
          asgName += '-' + command.stack;
        }
        if (!command.stack && command.freeFormDetails) {
          asgName += '-';
        }
        if (command.freeFormDetails) {
          asgName += '-' + command.freeFormDetails;
        }
        description = 'Create New Server Group in cluster ' + asgName;
      }

      return executeTask({
        job: [
          convertServerGroupCommandToDeployConfiguration(command)
        ],
        application: applicationName,
        description: description
      });
    }

    return {
      cloneServerGroup: cloneServerGroup,
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
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
