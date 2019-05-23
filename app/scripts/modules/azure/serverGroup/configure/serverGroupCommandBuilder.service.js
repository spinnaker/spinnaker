'use strict';

const angular = require('angular');

import { NameUtils } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroupCommandBuilder.service', [
    require('../../image/image.reader').name,
    require('../serverGroup.transformer').name,
  ])
  .factory('azureServerGroupCommandBuilder', [
    '$q',
    'azureImageReader',
    'azureServerGroupTransformer',
    function($q, azureImageReader, azureServerGroupTransformer) {
      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};

        var imageLoader = azureImageReader.findImages({ provider: 'azure' });

        var defaultCredentials = defaults.account || application.defaultCredentials.azure;
        var defaultRegion = defaults.region || application.defaultRegions.azure;

        return $q
          .all({
            images: imageLoader,
          })
          .then(function(backingData) {
            return {
              application: application.name,
              credentials: defaultCredentials,
              region: defaultRegion,
              images: backingData.images,
              loadBalancers: [],
              selectedVnetSubnets: [],
              strategy: '',
              sku: {
                capacity: 1,
              },
              zonesEnabled: false,
              zones: [],
              instanceTags: {},
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

        var serverGroupName = NameUtils.parseServerGroupName(serverGroup.name);

        var command = {
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
          if (
            typeof serverGroup.customScriptsSettings.fileUris !== 'undefined' &&
            serverGroup.customScriptsSettings.fileUris != ''
          ) {
            azureServerGroupTransformer.parseCustomScriptsSettings(serverGroup, command);
          }
        }

        return $q.when(command);
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster) {
        var pipelineCluster = _.cloneDeep(originalCluster);
        var region = pipelineCluster.region;
        var commandOptions = { account: pipelineCluster.account, region: region };
        var asyncLoader = $q.all({
          command: buildNewServerGroupCommand(application, commandOptions),
        });

        return asyncLoader.then(function(asyncData) {
          var command = asyncData.command;

          var viewState = {
            disableImageSelection: true,
            useSimpleCapacity: true,
            mode: 'editPipeline',
            submitButtonLabel: 'Done',
            instanceProfile: originalCluster.viewState.instanceProfile,
            instanceTypeDetails: originalCluster.viewState.instanceTypeDetails,
          };

          var viewOverrides = {
            region: region,
            credentials: pipelineCluster.account,
            viewState: viewState,
          };
          if (originalCluster.viewState.instanceTypeDetails) {
            viewOverrides.instanceType = originalCluster.viewState.instanceTypeDetails.name;
          }

          pipelineCluster.strategy = pipelineCluster.strategy || '';

          var extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);

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
