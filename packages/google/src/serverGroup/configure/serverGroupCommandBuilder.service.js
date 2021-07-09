'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { AccountService, ExpectedArtifactService, INSTANCE_TYPE_SERVICE } from '@spinnaker/core';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from '../../common/xpnNaming.gce.service';
import { GCEProviderSettings } from '../../gce.settings';
import { parseHealthCheckUrl } from '../../healthCheck/healthCheckUtils';

import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE } from './../../instance/custom/customInstanceBuilder.gce.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE } from './wizard/hiddenMetadataKeys.value';

export const GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE =
  'spinnaker.gce.serverGroupCommandBuilder.service';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE, [
    INSTANCE_TYPE_SERVICE,
    GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
    GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE,
    GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE,
  ])
  .factory('gceServerGroupCommandBuilder', [
    '$q',
    'instanceTypeService',
    'gceCustomInstanceBuilderService',
    'gceServerGroupHiddenMetadataKeys',
    'gceXpnNamingService',
    function (
      $q,
      instanceTypeService,
      gceCustomInstanceBuilderService,
      gceServerGroupHiddenMetadataKeys,
      gceXpnNamingService,
    ) {
      // Two assumptions here:
      //   1) All GCE machine types are represented in the tree of choices.
      //   2) Each machine type appears in exactly one category.
      function determineInstanceCategoryFromInstanceType(command) {
        return instanceTypeService.getCategories('gce').then(function (categories) {
          categories.forEach(function (category) {
            category.families.forEach(function (family) {
              family.instanceTypes.forEach(function (instanceType) {
                if (instanceType.name === command.instanceType) {
                  command.viewState.instanceProfile = category.type;
                }
              });
            });
          });
        });
      }

      function extractNetworkName(serverGroup) {
        const projectId = gceXpnNamingService.deriveProjectId(serverGroup.launchConfig.instanceTemplate);
        const networkUrl = _.get(serverGroup, 'launchConfig.instanceTemplate.properties.networkInterfaces[0].network');
        return gceXpnNamingService.decorateXpnResourceIfNecessary(projectId, networkUrl);
      }

      function extractSubnetName(serverGroup) {
        const projectId = gceXpnNamingService.deriveProjectId(serverGroup.launchConfig.instanceTemplate);
        const subnetworkUrl = _.get(
          serverGroup,
          'launchConfig.instanceTemplate.properties.networkInterfaces[0].subnetwork',
        );
        return gceXpnNamingService.decorateXpnResourceIfNecessary(projectId, subnetworkUrl);
      }

      function determineAssociatePublicIPAddress(serverGroup) {
        return _.has(serverGroup, 'launchConfig.instanceTemplate.properties.networkInterfaces[0].accessConfigs');
      }

      function extractLoadBalancers(asg) {
        return ['load-balancer-names', 'global-load-balancer-names'].reduce((loadBalancers, property) => {
          if (asg[property]) {
            loadBalancers = loadBalancers.concat(asg[property]);
          }
          return loadBalancers;
        }, []);
      }

      function extractLoadBalancersFromMetadata(metadata) {
        return ['load-balancer-names', 'global-load-balancer-names'].reduce((loadBalancers, property) => {
          if (metadata[property]) {
            loadBalancers = loadBalancers.concat(metadata[property].split(','));
          }
          return loadBalancers;
        }, []);
      }

      function populateDisksFromExisting(disks, command) {
        disks = disks.map((disk, index) => {
          if (index === 0) {
            return {
              type: disk.initializeParams.diskType,
              sizeGb: disk.initializeParams.diskSizeGb,
            };
          } else {
            return {
              type: disk.initializeParams.diskType,
              sizeGb: disk.initializeParams.diskSizeGb,
              sourceImage: _.last(_.get(disk, 'initializeParams.sourceImage', '').split('/')) || null,
            };
          }
        });
        const localSSDDisks = disks.filter((disk) => disk.type === 'local-ssd');
        const persistentDisks = disks.filter((disk) => disk.type.startsWith('pd-'));

        if (persistentDisks.length) {
          command.disks = persistentDisks.concat(localSSDDisks);
          return instanceTypeService
            .getInstanceTypeDetails(
              command.selectedProvider,
              _.includes(command.instanceType, 'custom-') ? 'buildCustom' : command.instanceType,
            )
            .then((instanceTypeDetails) => {
              command.viewState.instanceTypeDetails = instanceTypeDetails;
              calculateOverriddenStorageDescription(instanceTypeDetails, command);
            });
        } else {
          command.disks = [{ type: 'pd-ssd', sizeGb: 10 }].concat(localSSDDisks);
          return $q.when(null);
        }
      }

      function populateDisksFromPipeline(command) {
        const persistentDisks = getPersistentDisks(command);
        const localSSDDisks = getLocalSSDDisks(command);

        if (persistentDisks.length) {
          command.disks = persistentDisks.concat(localSSDDisks);
          return instanceTypeService
            .getInstanceTypeDetails(
              command.selectedProvider,
              _.includes(command.instanceType, 'custom-') ? 'buildCustom' : command.instanceType,
            )
            .then((instanceTypeDetails) => {
              command.viewState.instanceTypeDetails = instanceTypeDetails;
              calculateOverriddenStorageDescription(instanceTypeDetails, command);
            });
        } else {
          command.disks = [{ type: 'pd-ssd', sizeGb: 10 }].concat(localSSDDisks);
          return $q.when(null);
        }
      }

      function getLocalSSDDisks(command) {
        return (command.disks || []).filter((disk) => disk.type === 'local-ssd');
      }

      function getPersistentDisks(command) {
        return (command.disks || []).filter((disk) => disk.type.startsWith('pd-'));
      }

      function calculatePersistentDiskOverriddenStorageDescription(command) {
        const persistentDisks = getPersistentDisks(command);

        const diskCountBySizeGb = new Map();
        persistentDisks.forEach((disk) => {
          if (diskCountBySizeGb.has(disk.sizeGb)) {
            diskCountBySizeGb.set(disk.sizeGb, diskCountBySizeGb.get(disk.sizeGb) + 1);
          } else {
            diskCountBySizeGb.set(disk.sizeGb, 1);
          }
        });

        return Array.from(diskCountBySizeGb)
          .sort(([sizeA], [sizeB]) => sizeB - sizeA)
          .map(([sizeGb, count]) => count + '×' + sizeGb)
          .join(', ');
      }

      function calculateOverriddenStorageDescription(instanceTypeDetails, command) {
        if (instanceTypeDetails.storage.localSSDSupported) {
          if (getLocalSSDDisks(command).length !== instanceTypeDetails.storage.count) {
            command.viewState.overriddenStorageDescription = getLocalSSDDisks(command).length + '×375';
          }
        } else {
          const persistentDisks = getPersistentDisks(command);
          const overrideStorageDescription =
            persistentDisks.some((disk) => disk.sizeGb !== instanceTypeDetails.storage.size) ||
            persistentDisks.length !== instanceTypeDetails.storage.count;

          if (overrideStorageDescription) {
            command.viewState.overriddenStorageDescription = calculatePersistentDiskOverriddenStorageDescription(
              command,
            );
          }
        }
      }

      function populateAvailabilityPolicies(scheduling, command) {
        if (scheduling) {
          command.preemptible = scheduling.preemptible;
          command.automaticRestart = scheduling.automaticRestart;
          command.onHostMaintenance = scheduling.onHostMaintenance;
        } else {
          command.preemptible = false;
          command.automaticRestart = true;
          command.onHostMaintenance = 'MIGRATE';
        }
      }

      function populateAutoHealingPolicy(serverGroup, command) {
        const autoHealingPolicy = serverGroup.autoHealingPolicy;
        if (autoHealingPolicy) {
          const healthCheckUrl = autoHealingPolicy.healthCheckUrl
            ? autoHealingPolicy.healthCheckUrl
            : autoHealingPolicy.healthCheck;

          if (healthCheckUrl) {
            const { healthCheckName, healthCheckKind } = parseHealthCheckUrl(healthCheckUrl);
            command.autoHealingPolicy = {
              healthCheck: healthCheckName,
              healthCheckKind: healthCheckKind,
              healthCheckUrl: healthCheckUrl,
              initialDelaySec: autoHealingPolicy.initialDelaySec,
            };
          }

          const maxUnavailable = autoHealingPolicy.maxUnavailable;
          if (maxUnavailable) {
            command.autoHealingPolicy.maxUnavailable = maxUnavailable;
            command.viewState.maxUnavailableMetric = typeof maxUnavailable.percent === 'number' ? 'percent' : 'fixed';
          }
        }
      }

      function populateShieldedVmConfig(serverGroup, command) {
        command.enableSecureBoot = serverGroup.enableSecureBoot;
        command.enableVtpm = serverGroup.enableVtpm;
        command.enableIntegrityMonitoring = serverGroup.enableIntegrityMonitoring;
      }

      function populateCustomMetadata(metadataItems, command) {
        // Hide metadata items in the wizard.
        if (metadataItems) {
          let customUserData = '';
          let customUserDataKeys = [];
          if (angular.isArray(metadataItems)) {
            const customUserDataItem = metadataItems.find((metadataItem) => metadataItem.key === 'customUserData');
            if (customUserDataItem) {
              customUserData = customUserDataItem.value;
              customUserDataKeys = getCustomUserDataKeys(customUserData);
              command.userData = customUserData;
            }
            metadataItems.forEach(function (metadataItem) {
              if (
                !_.includes(customUserDataKeys, metadataItem.key) &&
                !_.includes(gceServerGroupHiddenMetadataKeys, metadataItem.key)
              ) {
                command.instanceMetadata[metadataItem.key] = metadataItem.value;
              }
            });
          } else {
            if (metadataItems.customUserData) {
              customUserData = metadataItems.customUserData;
              customUserDataKeys = getCustomUserDataKeys(customUserData);
              command.userData = customUserData;
              metadataItems = _.omit(metadataItems, customUserDataKeys);
            }
            angular.extend(command.instanceMetadata, _.omit(metadataItems, gceServerGroupHiddenMetadataKeys));
          }
        }

        if (command.labels['spinnaker-moniker-sequence']) {
          delete command.labels['spinnaker-moniker-sequence'];
        }
      }

      function getCustomUserDataKeys(customUserData) {
        const customUserDataKeys = [];
        customUserData.split(/\n|,/).forEach(function (userDataItem) {
          const customUserDataKey = userDataItem.split('=')[0];
          customUserDataKeys.push(customUserDataKey);
        });
        return customUserDataKeys;
      }

      function populateTags(instanceTemplateTags, command) {
        if (instanceTemplateTags && instanceTemplateTags.items) {
          _.map(instanceTemplateTags.items, function (tag) {
            command.tags.push({ value: tag });
          });
        }
      }

      function populateLabels(instanceTemplateLabels, command) {
        if (instanceTemplateLabels) {
          Object.assign(command.labels, instanceTemplateLabels);

          if (command.labels['spinnaker-region']) {
            delete command.labels['spinnaker-region'];
          }

          if (command.labels['spinnaker-server-group']) {
            delete command.labels['spinnaker-server-group'];
          }

          // Need to delete the sequence, otherwise it won't increment
          if (command.labels['spinnaker-moniker-sequence']) {
            delete command.labels['spinnaker-moniker-sequence'];
          }
        }
      }

      function populateAuthScopes(serviceAccounts, command) {
        if (serviceAccounts && serviceAccounts.length) {
          command.serviceAccountEmail = serviceAccounts[0].email;
          command.authScopes = _.map(serviceAccounts[0].scopes, (authScope) => {
            return authScope.replace('https://www.googleapis.com/auth/', '');
          });
        } else {
          command.authScopes = [];
        }
      }

      function attemptToSetValidCredentials(application, defaultCredentials, command) {
        return AccountService.listAccounts('gce').then(function (gceAccounts) {
          const gceAccountNames = _.map(gceAccounts, 'name');
          const firstGCEAccount = gceAccountNames[0];

          const defaultCredentialsAreValid = defaultCredentials && gceAccountNames.includes(defaultCredentials);

          command.credentials = defaultCredentialsAreValid ? defaultCredentials : firstGCEAccount || 'my-account-name';
        });
      }

      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};

        const defaultCredentials = defaults.account || GCEProviderSettings.defaults.account;
        const defaultRegion = defaults.region || GCEProviderSettings.defaults.region;
        const defaultZone = defaults.zone || GCEProviderSettings.defaults.zone;
        const associatePublicIpAddress = _.has(application, 'attributes.providerSettings.gce.associatePublicIpAddress')
          ? application.attributes.providerSettings.gce.associatePublicIpAddress
          : true;

        const command = {
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
          zone: defaultZone,
          regional: false, // TODO(duftler): Externalize this default alongside defaultRegion and defaultZone.
          selectZones: false, // Explicitly select zones for regional server groups.
          distributionPolicy: { zones: [] },
          network: 'default',
          associatePublicIpAddress: associatePublicIpAddress,
          canIpForward: false,
          strategy: '',
          capacity: {
            min: 0,
            max: 0,
            desired: 1,
          },
          backendServiceMetadata: [],
          minCpuPlatform: '(Automatic)',
          disks: [
            { type: 'pd-ssd', sizeGb: 10 },
            { type: 'local-ssd', sizeGb: 375 },
          ],
          imageSource: 'priorStage',
          instanceMetadata: {},
          tags: [],
          labels: {},
          enableSecureBoot: false,
          enableVtpm: false,
          enableIntegrityMonitoring: false,
          preemptible: false,
          automaticRestart: true,
          onHostMaintenance: 'MIGRATE',
          serviceAccountEmail: 'default',
          authScopes: ['cloud.useraccounts.readonly', 'devstorage.read_only', 'logging.write', 'monitoring.write'],
          enableTraffic: true,
          cloudProvider: 'gce',
          selectedProvider: 'gce',
          availabilityZones: [],
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useSimpleCapacity: true,
            usePreferredZones: true,
            listImplicitSecurityGroups: false,
            mode: defaults.mode || 'create',
            disableStrategySelection: true,
            expectedArtifacts: [],
          },
        };

        if (
          application.attributes &&
          application.attributes.platformHealthOnlyShowOverride &&
          application.attributes.platformHealthOnly
        ) {
          command.interestingHealthProviderNames = ['Google'];
        }

        return attemptToSetValidCredentials(application, defaultCredentials, command).then(() => command);
      }

      // Only used to prepare view requiring template selecting
      function buildNewServerGroupCommandForPipeline(currentStage, pipeline) {
        const expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(currentStage, pipeline);
        return $q.when({
          viewState: {
            pipeline,
            expectedArtifacts: expectedArtifacts,
            requiresTemplateSelection: true,
            stage: currentStage,
          },
        });
      }

      function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
        mode = mode || 'clone';
        const moniker = serverGroup.moniker;

        const command = {
          application: application.name,
          autoscalingPolicy: _.cloneDeep(serverGroup.autoscalingPolicy),
          strategy: '',
          stack: moniker.stack,
          freeFormDetails: moniker.detail,
          credentials: serverGroup.account,
          loadBalancers: extractLoadBalancers(serverGroup.asg),
          loadBalancingPolicy: _.cloneDeep(serverGroup.loadBalancingPolicy),
          backendServiceMetadata: serverGroup.asg['backend-service-names'],
          securityGroups: serverGroup.securityGroups,
          region: serverGroup.region,
          capacity: {
            min: serverGroup.asg.minSize,
            max: serverGroup.asg.maxSize,
            desired: serverGroup.asg.desiredCapacity,
          },
          regional: serverGroup.regional,
          network: extractNetworkName(serverGroup),
          subnet: extractSubnetName(serverGroup),
          associatePublicIpAddress: determineAssociatePublicIPAddress(serverGroup),
          canIpForward: serverGroup.canIpForward,
          minCpuPlatform: serverGroup.launchConfig.minCpuPlatform || '(Automatic)',
          instanceMetadata: {},
          tags: [],
          labels: {},
          availabilityZones: [],
          enableSecureBoot: serverGroup.enableSecureBoot,
          enableVtpm: serverGroup.enableVtpm,
          enableIntegrityMonitoring: serverGroup.enableIntegrityMonitoring,
          enableTraffic: true,
          cloudProvider: 'gce',
          selectedProvider: 'gce',
          distributionPolicy: {
            zones: serverGroup.distributionPolicy ? serverGroup.distributionPolicy.zones : [],
            targetShape: serverGroup.distributionPolicy ? serverGroup.distributionPolicy.targetShape : null,
          },
          selectZones: serverGroup.selectZones,
          source: {
            account: serverGroup.account,
            region: serverGroup.region,
            serverGroupName: serverGroup.name,
            asgName: serverGroup.name,
          },
          viewState: {
            allImageSelection: null,
            useSimpleCapacity: !serverGroup.autoscalingPolicy,
            usePreferredZones: false,
            listImplicitSecurityGroups: false,
            mode: mode,
          },
        };

        if (!command.regional) {
          command.zone = serverGroup.zones[0];
          command.source.zone = serverGroup.zones[0];
        }

        if (
          application.attributes &&
          application.attributes.platformHealthOnlyShowOverride &&
          application.attributes.platformHealthOnly
        ) {
          command.interestingHealthProviderNames = ['Google'];
        }

        populateAutoHealingPolicy(serverGroup, command);

        if (serverGroup.launchConfig) {
          const instanceType = serverGroup.launchConfig.instanceType;
          angular.extend(command, {
            instanceType: instanceType,
          });

          if (_.includes(instanceType, 'custom-')) {
            command.viewState.customInstance = gceCustomInstanceBuilderService.parseInstanceTypeString(instanceType);
            command.viewState.instanceProfile = 'buildCustom';
          }

          command.viewState.imageId = serverGroup.launchConfig.imageId;
          return determineInstanceCategoryFromInstanceType(command).then(function () {
            populateAvailabilityPolicies(serverGroup.launchConfig.instanceTemplate.properties.scheduling, command);
            populateCustomMetadata(serverGroup.launchConfig.instanceTemplate.properties.metadata.items, command);
            populateTags(serverGroup.launchConfig.instanceTemplate.properties.tags, command);
            populateLabels(serverGroup.instanceTemplateLabels, command);
            populateAuthScopes(serverGroup.launchConfig.instanceTemplate.properties.serviceAccounts, command);

            return populateDisksFromExisting(serverGroup.launchConfig.instanceTemplate.properties.disks, command).then(
              function () {
                return command;
              },
            );
          });
        }

        return $q.when(command);
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster, currentStage, pipeline) {
        const pipelineCluster = _.cloneDeep(originalCluster);
        const region = Object.keys(pipelineCluster.availabilityZones)[0];
        const zone = pipelineCluster.zone;
        const instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType(
          'gce',
          pipelineCluster.instanceType,
        );

        const commandOptions = { account: pipelineCluster.account, region: region, zone: zone };
        return $q
          .all([buildNewServerGroupCommand(application, commandOptions), instanceTypeCategoryLoader])
          .then(function ([command, instanceProfile]) {
            const expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
              currentStage,
              pipeline,
            );
            const viewState = {
              pipeline,
              stage: currentStage,
              instanceProfile,
              disableImageSelection: true,
              expectedArtifacts: expectedArtifacts,
              showImageSourceSelector: true,
              useSimpleCapacity: !pipelineCluster.autoscalingPolicy,
              mode: 'editPipeline',
              submitButtonLabel: 'Done',
              customInstance:
                instanceProfile === 'buildCustom'
                  ? gceCustomInstanceBuilderService.parseInstanceTypeString(pipelineCluster.instanceType)
                  : null,
              templatingEnabled: true,
            };

            const viewOverrides = {
              region: region,
              credentials: pipelineCluster.account,
              enableTraffic: !pipelineCluster.disableTraffic,
              viewState: viewState,
            };

            pipelineCluster.strategy = pipelineCluster.strategy || '';

            const extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);

            return populateDisksFromPipeline(extendedCommand).then(function () {
              const instanceMetadata = extendedCommand.instanceMetadata;
              extendedCommand.loadBalancers = extractLoadBalancersFromMetadata(instanceMetadata);
              extendedCommand.backendServiceMetadata = instanceMetadata['backend-service-names']
                ? instanceMetadata['backend-service-names'].split(',')
                : [];
              extendedCommand.minCpuPlatform = pipelineCluster.minCpuPlatform || '(Automatic)';
              extendedCommand.instanceMetadata = {};
              populateCustomMetadata(instanceMetadata, extendedCommand);
              populateAutoHealingPolicy(pipelineCluster, extendedCommand);
              populateShieldedVmConfig(pipelineCluster, extendedCommand);

              const instanceTemplateTags = { items: extendedCommand.tags };
              extendedCommand.tags = [];
              populateTags(instanceTemplateTags, extendedCommand);

              return extendedCommand;
            });
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
