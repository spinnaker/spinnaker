'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { NameUtils } from '@spinnaker/core';
import { AZURE_IMAGE_IMAGE_READER } from '../../image/image.reader';
import { AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from '../serverGroup.transformer';

export const AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE =
  'spinnaker.azure.serverGroupCommandBuilder.service';
export const name = AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE; // for backwards compatibility
angular
  .module(AZURE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE, [
    AZURE_IMAGE_IMAGE_READER,
    AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  ])
  .factory('azureServerGroupCommandBuilder', [
    '$q',
    'azureImageReader',
    'azureServerGroupTransformer',
    function ($q, azureImageReader, azureServerGroupTransformer) {
      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};

        const defaultCredentials = defaults.account || application.defaultCredentials.azure;
        const defaultRegion = defaults.region || application.defaultRegions.azure;

        return azureImageReader.findImages({ provider: 'azure' }).then(function (images) {
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
      function buildNewServerGroupCommandForPipeline() {
        return $q.when({
          viewState: {
            requiresTemplateSelection: true,
          },
        });
      }

      function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
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
            azureServerGroupTransformer.parseCustomScriptsSettings(serverGroup, command);
          }
        }

        return $q.when(command);
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster) {
        const pipelineCluster = _.cloneDeep(originalCluster);
        const region = pipelineCluster.region;

        const commandOptions = { account: pipelineCluster.account, region: region };
        return buildNewServerGroupCommand(application, commandOptions).then(function (command) {
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

          const extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);

          return extendedCommand;
        });
      }

      return {
        buildNewServerGroupCommand: buildNewServerGroupCommand,
        buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
        buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
        buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
      };
    },
  ]);
