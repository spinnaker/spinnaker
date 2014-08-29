'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('orcaService', function(settings, Restangular) {

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);
      RestangularConfigurer.setDefaultHeaders( {'Content-Type':'application/context+json'} );
    }).all('ops');

    function destroyServerGroup(serverGroup, applicationName) {
      return endpoint.post({
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
      return endpoint.post({
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
      return endpoint.post({
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
      return endpoint.post({
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
      return endpoint.post({
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

    return {
      destroyServerGroup: destroyServerGroup,
      disableServerGroup: disableServerGroup,
      enableServerGroup: enableServerGroup,
      resizeServerGroup: resizeServerGroup,
      terminateInstance: terminateInstance
    };
  });
