import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';
import { AzureImageReader } from '../../image/image.reader';
import { AzureServerGroupTransformer } from '../serverGroup.transformer';

export class AzureServerGroupCommandBuilder {
  constructor($q) {
    this.$q = $q;
    this.azureImageReader = new AzureImageReader();
    this.azureServerGroupTransformer = new AzureServerGroupTransformer();
  }

  resolve(value) {
    return this.$q && this.$q.when ? this.$q.when(value) : Promise.resolve(value);
  }

  buildNewServerGroupCommand(application, defaults) {
    defaults = defaults || {};

    const defaultCredentials = defaults.account || application.defaultCredentials.azure;
    const defaultRegion = defaults.region || application.defaultRegions.azure;

    return this.azureImageReader.findImages({ provider: 'azure' }).then(function (images) {
      return {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        images,
        loadBalancers: [],
        selectedVnetSubnets: [],
        strategy: '',
        sku: {
          capacity: 1,
        },
        zonesEnabled: false,
        zones: [],
        instanceTags: {},
        dataDisks: [],
        selectedProvider: 'azure',
        viewState: {
          instanceProfile: 'custom',
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: true,
          usePreferredZones: true,
          mode: defaults.mode || 'create',
          disableStrategySelection: true,
          loadBalancersConfigured: false,
          networkSettingsConfigured: false,
          securityGroupsConfigured: false,
        },
        enableInboundNAT: false,
      };
    });
  }

  // Only used to prepare view requiring template selecting
  buildNewServerGroupCommandForPipeline() {
    return this.resolve({
      viewState: {
        requiresTemplateSelection: true,
      },
    });
  }

  buildServerGroupCommandFromExisting(application, serverGroup, mode) {
    mode = mode || 'clone';

    const serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);

    const command = {
      application: application.name,
      strategy: '',
      stack: serverGroupName.stack,
      freeFormDetails: serverGroupName.freeFormDetails,
      credentials: serverGroup.account,
      loadBalancers: serverGroup.loadBalancers,
      selectedSubnets: serverGroup.selectedVnetSubnets,
      selectedVnet: serverGroup.selectedVnet,
      securityGroups: serverGroup.securityGroups,
      loadBalancerName: serverGroup.loadBalancerName,
      loadBalancerType: serverGroup.loadBalancerType,
      securityGroupName: serverGroup.securityGroupName,
      region: serverGroup.region,
      vnet: serverGroup.vnet,
      vnetResourceGroup: serverGroup.vnetResourceGroup,
      subnet: serverGroup.subnet,
      zones: serverGroup.zones,
      zonesEnabled: serverGroup.zones && serverGroup.zones.length > 0,
      instanceTags: {},
      dataDisks: serverGroup.dataDisks,
      sku: serverGroup.sku,
      capacity: {
        min: serverGroup.capacity.min,
        max: serverGroup.capacity.max,
        desired: serverGroup.capacity.desired,
      },
      tags: [],
      instanceType: serverGroup.sku.name,
      selectedProvider: 'azure',
      source: {
        account: serverGroup.account,
        region: serverGroup.region,
        serverGroupName: serverGroup.name,
        asgName: serverGroup.name,
      },
      viewState: {
        allImageSelection: null,
        useAllImageSelection: false,
        useSimpleCapacity: true,
        usePreferredZones: false,
        listImplicitSecurityGroups: false,
        mode: mode,
        disableStrategySelection: true,
      },
      enableInboundNAT: serverGroup.enableInboundNAT,
    };

    if (typeof serverGroup.customScriptsSettings !== 'undefined') {
      command.customScriptsSettings = {};
      command.customScriptsSettings.commandToExecute = serverGroup.customScriptsSettings.commandToExecute;
      if (!_.isEmpty(serverGroup.customScriptsSettings.fileUris)) {
        this.azureServerGroupTransformer.parseCustomScriptsSettings(serverGroup, command);
      }
    }

    return this.azureImageReader.findImages({ provider: 'azure' }).then((images) => {
      const sourceImage = serverGroup.image || {};
      const imageName = sourceImage.imageName || serverGroup.imageName || serverGroup.amiName;
      const selectedImage = images.find((image) => image.imageName === imageName);

      command.images = images;
      if (sourceImage.isCustom || sourceImage.uri) {
        command.image = { ...sourceImage, isCustom: true, region: sourceImage.region || serverGroup.region };
      } else if (imageName) {
        command.imageName = imageName;
        command.selectedImage = selectedImage || sourceImage;
      }

      return command;
    });
  }

  buildServerGroupCommandFromPipeline(application, originalCluster) {
    const pipelineCluster = _.cloneDeep(originalCluster);
    const region = pipelineCluster.region;

    const commandOptions = { account: pipelineCluster.account, region: region };
    return this.buildNewServerGroupCommand(application, commandOptions).then(function (command) {
      const viewState = {
        disableImageSelection: true,
        useSimpleCapacity: true,
        mode: 'editPipeline',
        submitButtonLabel: 'Done',
        instanceProfile: originalCluster.viewState.instanceProfile,
        instanceTypeDetails: originalCluster.viewState.instanceTypeDetails,
      };

      const viewOverrides = {
        region: region,
        credentials: pipelineCluster.account,
        viewState: viewState,
      };
      if (originalCluster.viewState.instanceTypeDetails) {
        viewOverrides.instanceType = originalCluster.viewState.instanceTypeDetails.name;
      }

      pipelineCluster.strategy = pipelineCluster.strategy || '';

      const extendedCommand = _.extend({}, command, pipelineCluster, viewOverrides);

      return extendedCommand;
    });
  }
}
