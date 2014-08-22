'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('orcaService', function(settings, Restangular) {

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);
    }).all('ops');

    function destroyServerGroup(serverGroup) {
      return endpoint.post([
        {
          asgName: serverGroup.name,
          type: 'destroyAsg',
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          user: 'chrisb'
        }
      ]);
    }

    function disableServerGroup(serverGroup) {
      return endpoint.post([
        {
          asgName: serverGroup.name,
          type: 'disableAsg',
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          user: 'chrisb'
        }
      ]);
    }

    function resizeServerGroup(serverGroup, capacity) {
      return endpoint.post([
        {
          asgName: serverGroup.name,
          type: 'resizeAsg',
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          user: 'deckUser',
          capacity: capacity
        }
      ]);
    }

    function terminateInstance(instance) {
      return endpoint.post([{
        type: 'terminateInstances',
          instanceIds: [instance.instanceId],
        region: instance.region,
        credentials: instance.account,
        user: 'chrisb'
      }]);
    }

    return {
      destroyServerGroup: destroyServerGroup,
      disableServerGroup: disableServerGroup,
      resizeServerGroup: resizeServerGroup,
      terminateInstance: terminateInstance
    };
  });
