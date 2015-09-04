'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.transformer.service', [
    require('../../../../core/cloudProvider/serviceDelegate.service.js'),
  ])
  .factory('serverGroupTransformer', function (serviceDelegate) {

    function convertServerGroupCommandToDeployConfiguration(base) {
      var service = serviceDelegate.getDelegate(base.selectedProvider, 'serverGroup.transformer');
      return service ? service.convertServerGroupCommandToDeployConfiguration(base) : null;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration
    };

  }).name;
