'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.transformer', [
  ])
  .factory('azureServerGroupTransformer', function () {

    function normalizeServerGroup(serverGroup) {
      return serverGroup;
    }

    function convertServerGroupCommandToDeployConfiguration(command) {
      var tempImage;

      if(command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline') {
        tempImage = {
          imageName: '${imageName}',
          isCustom: '${isCustom}',
          publisher: '${publisher}',
          offer: '${offer}',
          sku: '${imagesku}',
          version: '${version}',
          region: command.region,
          uri: '${uri}',
          ostype: '${ostype}',
        };
      } else {
        tempImage = command.selectedImage;
      }

      var configuration = {
        name: command.application,
        cloudProvider: command.selectedProvider,
        application: command.application,
        stack: command.stack,
        detail: command.freeFormDetails,
        account: command.credentials,
        selectedProvider: 'azure',
        capacity: {
          useSourceCapacity: false,
          min: command.sku.capacity,
          max: command.sku.capacity,
        },
        credentials: command.credentials,
        region: command.region,
        securityGroup: command.securityGroup,
        loadBalancerName: command.loadBalancerName,
        user: '[anonymous]',
        upgradePolicy: 'Manual',
        type: 'createServerGroup',
        image: tempImage,
        sku: {
          name: 'Standard_A1',
          tier: 'Standard',
          capacity: command.sku.capacity,
        },
        viewState: command.viewState,
        osConfig: {
          adminUserName: 'spinnakeruser',
          adminPassword: '!Qnti**234',
        },
      };

      if (typeof command.stack !== 'undefined') {
        configuration.name = configuration.name + '-' + command.stack;
      }
      if (typeof command.freeFormDetails !== 'undefined') {
        configuration.name = configuration.name + '-' + command.freeFormDetails;
      }

      return configuration;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
