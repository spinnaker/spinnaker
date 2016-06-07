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
          imageName: '',
          isCustom: 'true',
          publisher: '',
          offer: '',
          sku: '',
          version: '',
          region: command.region,
          uri: '',
          ostype: '',
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
        freeFormDetails: command.freeFormDetails,
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
          name: 'Standard_DS1_v2',
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

      // Default to an empty list of health provider names for now.
      configuration.interestingHealthProviderNames = [];

      return configuration;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup: normalizeServerGroup,
    };

  });
