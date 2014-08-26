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
        href: urlBuilder({
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
      return executeTask({
        job: [
          {
            // make consistent with orca input
            asgName: command.source.asgName,
            type: 'copyLastAsg',
            regions: [command.region],
            credentials: command.credentials,
            user: 'deckUser'
          }
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
      terminateInstance: terminateInstance
    };
  });
