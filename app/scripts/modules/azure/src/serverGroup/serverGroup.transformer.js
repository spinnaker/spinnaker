'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER = 'spinnaker.azure.serverGroup.transformer';
export const name = AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER; // for backwards compatibility
module(AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER, []).factory('azureServerGroupTransformer', function () {
  function normalizeServerGroup(serverGroup) {
    return serverGroup;
  }

  function parseCustomScriptsSettings(command, configuration) {
    /*
        At the first time this wizard pops up, the type of command.customScriptsSettings.fileUris is String. As for the following
        occurrences of its pop up with this field unchanged, its type becomes an array. So here differentiate the two scenarios
        to assign the correct value to model.
      */
    if (Array.isArray(command.customScriptsSettings.fileUris)) {
      configuration.customScriptsSettings.fileUris = command.customScriptsSettings.fileUris;
    } else {
      const fileUrisTemp = command.customScriptsSettings.fileUris;
      if (fileUrisTemp.includes(',')) {
        configuration.customScriptsSettings.fileUris = fileUrisTemp.split(',');
      } else if (fileUrisTemp.includes(';')) {
        configuration.customScriptsSettings.fileUris = fileUrisTemp.split(';');
      } else {
        configuration.customScriptsSettings.fileUris = [fileUrisTemp];
      }

      configuration.customScriptsSettings.fileUris.forEach(function (v, index) {
        configuration.customScriptsSettings.fileUris[index] = v.trim();
      });
    }
  }

  function convertServerGroupCommandToDeployConfiguration(command) {
    let tempImage;

    if (command.viewState.mode === 'editPipeline' || command.viewState.mode === 'createPipeline') {
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

    const configuration = {
      name: command.application,
      cloudProvider: command.selectedProvider,
      application: command.application,
      stack: command.stack,
      strategy: command.strategy,
      rollback: {
        onFailure: command.rollback ? command.rollback.onFailure : null,
      },
      scaleDown: command.scaleDown,
      maxRemainingAsgs: command.maxRemainingAsgs,
      delayBeforeDisableSec: command.delayBeforeDisableSec,
      delayBeforeScaleDownSec: command.delayBeforeScaleDownSec,
      allowDeleteActive: command.strategy === 'redblack' ? true : null,
      allowScaleDownActive: command.strategy === 'redblack' ? true : null,
      detail: command.freeFormDetails,
      freeFormDetails: command.freeFormDetails,
      account: command.credentials,
      selectedProvider: 'azure',
      vnet: command.vnet,
      vnetResourceGroup: command.selectedVnet.resourceGroup,
      subnet: command.subnet,
      useSourceCapacity: false,
      capacity: {
        min: command.sku.capacity,
        max: command.sku.capacity,
      },
      credentials: command.credentials,
      region: command.region,
      securityGroupName: command.securityGroupName,
      loadBalancerName: command.loadBalancerName,
      loadBalancerType: command.loadBalancerType,
      user: '[anonymous]',
      upgradePolicy: 'Manual',
      type: 'createServerGroup',
      image: tempImage,
      sku: {
        name: 'Standard_DS1_v2',
        tier: 'Standard',
        capacity: command.sku.capacity,
      },
      instanceTags: command.instanceTags,
      dataDisks: command.dataDisks,
      viewState: command.viewState,
      osConfig: {
        customData: command.osConfig ? command.osConfig.customData : null,
      },
      customScriptsSettings: {
        fileUris: null,
        commandToExecute: '',
      },
      zonesEnabled: command.zonesEnabled,
      zones: command.zonesEnabled ? command.zones : [],
      enableInboundNAT: command.enableInboundNAT,
    };

    if (typeof command.stack !== 'undefined') {
      configuration.name = configuration.name + '-' + command.stack;
    }
    if (typeof command.freeFormDetails !== 'undefined') {
      configuration.name = configuration.name + '-' + command.freeFormDetails;
    }

    if (typeof command.customScriptsSettings !== 'undefined') {
      configuration.customScriptsSettings.commandToExecute = command.customScriptsSettings.commandToExecute;
      if (!_.isEmpty(command.customScriptsSettings.fileUris)) {
        parseCustomScriptsSettings(command, configuration);
      }
    }

    if (command.instanceType) {
      const vmsku = command.instanceType;
      configuration.instanceType = command.instanceType;
      configuration.sku.name = vmsku;
      configuration.sku.tier = vmsku.substring(0, vmsku.indexOf('_'));
    }

    // Default to an empty list of health provider names for now.
    configuration.interestingHealthProviderNames = [];

    return configuration;
  }

  return {
    convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
    normalizeServerGroup: normalizeServerGroup,
    parseCustomScriptsSettings: parseCustomScriptsSettings,
  };
});
