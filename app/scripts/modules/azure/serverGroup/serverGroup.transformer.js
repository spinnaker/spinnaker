'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.transformer', [
  ])
  .factory('azureServerGroupTransformer', function () {

    function convertServerGroupCommandToDeployConfiguration(command) {
      var configuration = {
        name: command.application,
        cloudProvider: command.selectedProvider,
        application: command.application,
        stack: command.stack,
        detail: command.details,
        credentials: command.credentials,
        region: command.region,
        securityGroup: command.selectedSecurityGroup,
        loadBalancerName: command.loadBalancerName,
        user: '[anonymous]',
        upgradePolicy: 'Manual',
        type: 'createServerGroup',
        image: command.selectedImage,
        sku: {
          name: 'Standard_A1',
          tier: 'Standard',
          capacity: command.sku.capacity,
        },

        osConfig: {
          adminUserName: 'spinnakeruser',
          adminPassword: '!Qnti**234',
        },
      };

      if (typeof command.stack !== 'undefined') {
        configuration.name = configuration.name + '-' + command.stack;
      }
      if (typeof command.details !== 'undefined') {
        configuration.name = configuration.name + '-' + command.details;
      }

      return configuration;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
    };

  });
