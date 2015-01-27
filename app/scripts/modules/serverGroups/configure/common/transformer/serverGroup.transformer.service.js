'use strict';

angular
  .module('deckApp.serverGroup.transformer.service')
  .factory('serverGroupTransformer', function (serviceDelegate) {

    function convertServerGroupCommandToDeployConfiguration(base) {
      var service = serviceDelegate.getDelegate(base.provider, 'ServerGroupTransformer');
      return service ? service.convertServerGroupCommandToDeployConfiguration(base) : null;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration
    };

  });
