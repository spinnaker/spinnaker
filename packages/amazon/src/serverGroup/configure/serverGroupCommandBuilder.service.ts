import type { IQService } from 'angular';
import * as angular from 'angular';
import _ from 'lodash';

import type { Application, InstanceTypeService } from '@spinnaker/core';
import {
  AccountService,
  DeploymentStrategyRegistry,
  INSTANCE_TYPE_SERVICE,
  NameUtils,
  SubnetReader,
} from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import type { IAmazonLaunchTemplateOverrides, ILaunchTemplateData } from '../../domain';
import type { IAmazonServerGroup, IAmazonServerGroupView, INetworkInterface } from '../../domain';
import type {
  AwsServerGroupConfigurationService,
  IAmazonInstanceTypeOverride,
  IAmazonServerGroupCommand,
  IAmazonServerGroupCommandViewState,
  IAmazonServerGroupDeployConfiguration,
} from './serverGroupConfiguration.service';
import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

export const AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE =
  'spinnaker.amazon.serverGroupCommandBuilder.service';
export const name = AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE; // for backwards compatibility

export interface AwsServerGroupCommandBuilder {
  buildNewServerGroupCommand(
    application: Application,
    defaults?: { account?: string; region?: string; subnet?: string; mode?: string },
  ): PromiseLike<Partial<IAmazonServerGroupCommand>>;

  buildServerGroupCommandFromExisting(
    application: Application,
    serverGroup: IAmazonServerGroupView,
    mode?: string,
  ): PromiseLike<Partial<IAmazonServerGroupCommand>>;

  buildNewServerGroupCommandForPipeline(): PromiseLike<Partial<IAmazonServerGroupCommand>>;

  buildServerGroupCommandFromPipeline(
    application: Application,
    originalCluster: IAmazonServerGroupDeployConfiguration,
  ): PromiseLike<Partial<IAmazonServerGroupCommand>>;

  buildUpdateServerGroupCommand(serverGroup: IAmazonServerGroup): Partial<IAmazonServerGroupCommand>;
}

angular
  .module(AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE, [
    INSTANCE_TYPE_SERVICE,
    AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .factory('awsServerGroupCommandBuilder', [
    '$q',
    'instanceTypeService',
    'awsServerGroupConfigurationService',
    function (
      $q: IQService,
      instanceTypeService: InstanceTypeService,
      awsServerGroupConfigurationService: AwsServerGroupConfigurationService,
    ) {
      function buildNewServerGroupCommand(
        application: Application,
        defaults: { account?: string; region?: string; subnet?: string; mode?: string },
      ) {
        defaults = defaults || {};

        const credentialsLoader = AccountService.getCredentialsKeyedByAccount('aws');

        const defaultCredentials =
          defaults.account || application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
        const defaultRegion = defaults.region || application.defaultRegions.aws || AWSProviderSettings.defaults.region;
        const defaultSubnet = defaults.subnet || AWSProviderSettings.defaults.subnetType || '';

        const preferredZonesLoader = AccountService.getAvailabilityZonesForAccountAndRegion(
          'aws',
          defaultCredentials,
          defaultRegion,
        );

        return $q
          .all([preferredZonesLoader, credentialsLoader])
          .then(function ([preferredZones, credentialsKeyedByAccount]) {
            const credentials = credentialsKeyedByAccount[defaultCredentials];
            const keyPair = credentials ? credentials.defaultKeyPair : null;
            const applicationAwsSettings = application.attributes?.providerSettings?.aws ?? {};

            let defaultIamRole = AWSProviderSettings.defaults.iamRole || 'BaseIAMRole';
            defaultIamRole = defaultIamRole.replace('{{application}}', application.name);

            const useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

            const command: Partial<IAmazonServerGroupCommand> = {
              application: application.name,
              credentials: defaultCredentials,
              region: defaultRegion,
              strategy: '',
              capacity: {
                min: 1,
                max: 1,
                desired: 1,
              },
              targetHealthyDeployPercentage: 100,
              cooldown: 10,
              enabledMetrics: [],
              healthCheckType: 'EC2',
              healthCheckGracePeriod: 600,
              instanceMonitoring: false,
              ebsOptimized: false,
              selectedProvider: 'aws',
              iamRole: defaultIamRole,
              terminationPolicies: ['Default'],
              vpcId: null,
              subnetType: defaultSubnet,
              availabilityZones: preferredZones,
              keyPair: keyPair,
              suspendedProcesses: [],
              securityGroups: [],
              stack: '',
              freeFormDetails: '',
              spotPrice: '',
              tags: {},
              useAmiBlockDeviceMappings: useAmiBlockDeviceMappings,
              copySourceCustomBlockDeviceMappings: false, // default to using block device mappings from current instance type
              viewState: {
                instanceProfile: 'custom',
                useSimpleInstanceTypeSelector: true,
                useAllImageSelection: false,
                useSimpleCapacity: true,
                usePreferredZones: true,
                mode: defaults.mode || 'create',
                disableStrategySelection: true,
                dirty: {},
                submitButtonLabel: getSubmitButtonLabel(defaults.mode || 'create'),
              } as IAmazonServerGroupCommandViewState,
            };

            if (application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly) {
              command.interestingHealthProviderNames = ['Amazon'];
            }

            if (defaultCredentials === 'test' && AWSProviderSettings.serverGroups?.enableIPv6) {
              command.associateIPv6Address = true;
            }

            if (AWSProviderSettings.serverGroups?.enableIMDSv2) {
              /**
               * Older SDKs do not support IMDSv2. A timestamp can be optionally configured at which any apps created after can safely default to using IMDSv2.
               */
              const appAgeRequirement = AWSProviderSettings.serverGroups.defaultIMDSv2AppAgeLimit;
              const creationDate = application.attributes?.createTs;

              command.requireIMDSv2 = appAgeRequirement && creationDate && Number(creationDate) > appAgeRequirement;
            }

            return command;
          });
      }

      function buildServerGroupCommandFromPipeline(
        application: Application,
        originalCluster: IAmazonServerGroupDeployConfiguration,
      ) {
        const pipelineCluster = _.cloneDeep(originalCluster);
        const region = Object.keys(pipelineCluster.availabilityZones)[0];

        const instanceTypes = pipelineCluster.launchTemplateOverridesForInstanceType
          ? pipelineCluster.launchTemplateOverridesForInstanceType.map((o) => o.instanceType)
          : [pipelineCluster.instanceType];
        const instanceTypeCategoryLoader = instanceTypeService.getCategoryForMultipleInstanceTypes(
          'aws',
          instanceTypes,
        );

        const commandOptions = { account: pipelineCluster.account, region: region };
        const asyncLoader = $q.all([
          buildNewServerGroupCommand(application, commandOptions),
          instanceTypeCategoryLoader,
        ]);

        return asyncLoader.then(function ([command, instanceProfile]) {
          const zones = pipelineCluster.availabilityZones[region];
          const usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

          const viewState = {
            instanceProfile,
            disableImageSelection: true,
            useSimpleCapacity:
              pipelineCluster.capacity.min === pipelineCluster.capacity.max &&
              pipelineCluster.useSourceCapacity !== true,
            usePreferredZones: usePreferredZones,
            mode: 'editPipeline',
            submitButtonLabel: 'Done',
            templatingEnabled: true,
            existingPipelineCluster: true,
            dirty: {},
            useSimpleInstanceTypeSelector: isSimpleModeEnabled(pipelineCluster),
          };

          const viewOverrides = {
            region: region,
            credentials: pipelineCluster.account,
            availabilityZones: pipelineCluster.availabilityZones[region],
            iamRole: pipelineCluster.iamRole,
            viewState: viewState,
          };

          pipelineCluster.strategy = pipelineCluster.strategy || '';

          return angular.extend({}, command, pipelineCluster, viewOverrides);
        });
      }

      // Only used to prepare view requiring template selecting
      function buildNewServerGroupCommandForPipeline() {
        return $q.when({
          viewState: {
            requiresTemplateSelection: true,
          } as IAmazonServerGroupCommandViewState,
        });
      }

      function getSubmitButtonLabel(mode: string) {
        switch (mode) {
          case 'createPipeline':
            return 'Add';
          case 'editPipeline':
            return 'Done';
          case 'clone':
            return 'Clone';
          default:
            return 'Create';
        }
      }

      function buildUpdateServerGroupCommand(serverGroup: IAmazonServerGroup) {
        const command = ({
          type: 'modifyAsg',
          asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
          cooldown: serverGroup.asg.defaultCooldown,
          enabledMetrics: (serverGroup.asg?.enabledMetrics ?? []).map((m) => m.metric),
          healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
          healthCheckType: serverGroup.asg.healthCheckType,
          terminationPolicies: angular.copy(serverGroup.asg.terminationPolicies),
          credentials: serverGroup.account,
          capacityRebalance: serverGroup.asg.capacityRebalance,
        } as Partial<IAmazonServerGroupCommand>) as IAmazonServerGroupCommand;
        awsServerGroupConfigurationService.configureUpdateCommand(command);
        return command;
      }

      function buildServerGroupCommandFromExisting(
        application: Application,
        serverGroup: IAmazonServerGroupView,
        mode = 'clone',
      ) {
        const preferredZonesLoader = AccountService.getPreferredZonesByAccount('aws');
        const subnetsLoader = SubnetReader.listSubnets();

        const serverGroupName = NameUtils.parseServerGroupName(serverGroup.asg.autoScalingGroupName);

        let instanceTypes;
        if (serverGroup.mixedInstancesPolicy) {
          const ltOverrides = serverGroup.mixedInstancesPolicy?.launchTemplateOverridesForInstanceType;
          // note: single launch template case is currently the only supported case for mixed instances policy
          instanceTypes = ltOverrides
            ? ltOverrides.map((o) => o.instanceType)
            : [serverGroup.mixedInstancesPolicy?.launchTemplates[0]?.launchTemplateData?.instanceType];
        } else if (serverGroup.launchTemplate) {
          instanceTypes = [_.get(serverGroup, 'launchTemplate.launchTemplateData.instanceType')];
        } else if (serverGroup.launchConfig) {
          instanceTypes = [_.get(serverGroup, 'launchConfig.instanceType')];
        }
        const instanceTypeCategoryLoader = instanceTypeService.getCategoryForMultipleInstanceTypes(
          'aws',
          instanceTypes,
        );

        return $q
          .all([preferredZonesLoader, subnetsLoader, instanceTypeCategoryLoader])
          .then(([preferredZones, subnets, instanceProfile]) => {
            const zones = serverGroup.asg.availabilityZones.sort();
            let usePreferredZones = false;
            const preferredZonesForAccount = preferredZones[serverGroup.account];
            if (preferredZonesForAccount) {
              const preferredZones = preferredZonesForAccount[serverGroup.region].sort();
              usePreferredZones = zones.join(',') === preferredZones.join(',');
            }

            // These processes should never be copied over, as the affect launching instances and enabling traffic
            const enabledProcesses = ['Launch', 'Terminate', 'AddToLoadBalancer'];

            const applicationAwsSettings = application.attributes?.providerSettings?.aws ?? {};
            const useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

            const existingTags: { [key: string]: string } = {};
            // These tags are applied by Clouddriver (if configured to do so), regardless of what the user might enter
            // Might be worth feature flagging this if it turns out other folks are hard-coding these values
            const reservedTags = ['spinnaker:application', 'spinnaker:stack', 'spinnaker:details'];
            if (serverGroup.asg.tags) {
              serverGroup.asg.tags
                .filter((t) => !reservedTags.includes(t.key))
                .forEach((tag) => {
                  existingTags[tag.key] = tag.value;
                });
            }

            const command: Partial<IAmazonServerGroupCommand> = {
              application: application.name,
              strategy: '',
              stack: serverGroupName.stack,
              freeFormDetails: serverGroupName.freeFormDetails,
              credentials: serverGroup.account,
              cooldown: serverGroup.asg.defaultCooldown,
              enabledMetrics: _.get(serverGroup, 'asg.enabledMetrics', []).map((m) => m.metric),
              healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
              healthCheckType: serverGroup.asg.healthCheckType,
              terminationPolicies: serverGroup.asg.terminationPolicies,
              loadBalancers: serverGroup.asg.loadBalancerNames,
              region: serverGroup.region,
              useSourceCapacity: false,
              capacity: {
                min: serverGroup.asg.minSize,
                max: serverGroup.asg.maxSize,
                desired: serverGroup.asg.desiredCapacity,
              },
              targetHealthyDeployPercentage: 100,
              availabilityZones: zones,
              selectedProvider: 'aws',
              source: {
                account: serverGroup.account,
                region: serverGroup.region,
                asgName: serverGroup.asg.autoScalingGroupName,
              },
              suspendedProcesses: (serverGroup.asg.suspendedProcesses || [])
                .map((process) => process.processName)
                .filter((name) => !enabledProcesses.includes(name)),
              tags: Object.assign({}, serverGroup.tags, existingTags),
              targetGroups: serverGroup.targetGroups,
              useAmiBlockDeviceMappings: useAmiBlockDeviceMappings,
              copySourceCustomBlockDeviceMappings: mode === 'clone', // default to using block device mappings if not cloning
              viewState: {
                instanceProfile,
                useAllImageSelection: false,
                useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
                usePreferredZones: usePreferredZones,
                mode: mode,
                submitButtonLabel: getSubmitButtonLabel(mode),
                isNew: false,
                dirty: {},
              } as IAmazonServerGroupCommandViewState,
            };

            if (application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly) {
              command.interestingHealthProviderNames = ['Amazon'];
            }

            if (mode === 'editPipeline') {
              command.useSourceCapacity = true;
              command.viewState.useSimpleCapacity = false;
              command.strategy = 'redblack';
              const redblack = DeploymentStrategyRegistry.getStrategy('redblack');
              redblack.initializationMethod && redblack.initializationMethod(command);
              command.suspendedProcesses = [];
            }

            const vpcZoneIdentifier = serverGroup.asg.vpczoneIdentifier;
            if (vpcZoneIdentifier !== '') {
              const subnetId = vpcZoneIdentifier.split(',')[0];
              const subnet = subnets.find((x) => x.id === subnetId);
              command.subnetType = subnet.purpose;
              command.vpcId = subnet.vpcId;
            } else {
              command.subnetType = '';
              command.vpcId = null;
            }

            if (serverGroup.launchConfig) {
              angular.extend(command, {
                instanceType: serverGroup.launchConfig.instanceType,
                iamRole: serverGroup.launchConfig.iamInstanceProfile,
                keyPair: serverGroup.launchConfig.keyName,
                associatePublicIpAddress: serverGroup.launchConfig.associatePublicIpAddress,
                ramdiskId: serverGroup.launchConfig.ramdiskId,
                instanceMonitoring:
                  serverGroup.launchConfig.instanceMonitoring && serverGroup.launchConfig.instanceMonitoring.enabled,
                ebsOptimized: serverGroup.launchConfig.ebsOptimized,
                spotPrice: serverGroup.launchConfig.spotPrice,
              });
              if (serverGroup.launchConfig.userData) {
                command.base64UserData = serverGroup.launchConfig.userData;
              }
              command.viewState.imageId = serverGroup.launchConfig.imageId;
              command.viewState.useSimpleInstanceTypeSelector = true;
            }

            if (serverGroup.launchTemplate || serverGroup.mixedInstancesPolicy) {
              let launchTemplateData: ILaunchTemplateData, spotMaxPrice: string;
              if (serverGroup.launchTemplate) {
                launchTemplateData = serverGroup.launchTemplate.launchTemplateData;
                spotMaxPrice = launchTemplateData.instanceMarketOptions?.spotOptions?.maxPrice;
                command.instanceType = launchTemplateData.instanceType;
                command.viewState.useSimpleInstanceTypeSelector = true;
                if (launchTemplateData.userData) {
                  command.base64UserData = launchTemplateData.userData;
                }
              }

              if (serverGroup.mixedInstancesPolicy) {
                const mip = serverGroup.mixedInstancesPolicy;
                // note: single launch template case is currently the only supported case for mixed instances policy
                launchTemplateData = mip?.launchTemplates?.[0]?.launchTemplateData;
                spotMaxPrice = mip?.instancesDistribution?.spotMaxPrice;
                command.securityGroups = launchTemplateData.networkInterfaces
                  ? (launchTemplateData.networkInterfaces.find((ni) => ni.deviceIndex === 0) ?? {}).groups
                  : launchTemplateData.securityGroups;
                command.onDemandAllocationStrategy = mip.instancesDistribution.onDemandAllocationStrategy;
                command.onDemandBaseCapacity = mip.instancesDistribution.onDemandBaseCapacity;
                command.onDemandPercentageAboveBaseCapacity =
                  mip.instancesDistribution.onDemandPercentageAboveBaseCapacity;
                command.spotAllocationStrategy = mip.instancesDistribution.spotAllocationStrategy;
                command.spotInstancePools = mip.instancesDistribution.spotInstancePools;

                // 'launchTemplateOverridesForInstanceType' is used for multiple instance types case, 'instanceType' is used for all other cases.
                if (mip.launchTemplateOverridesForInstanceType) {
                  command.launchTemplateOverridesForInstanceType = getInstanceTypesWithPriority(
                    mip.launchTemplateOverridesForInstanceType,
                  );
                } else {
                  command.instanceType = launchTemplateData.instanceType;
                }

                command.viewState.useSimpleInstanceTypeSelector = isSimpleModeEnabled(command);
              }

              const ipv6AddressCount = _.get(launchTemplateData, 'networkInterfaces[0]');

              const asgSettings = AWSProviderSettings.serverGroups;
              const isTestEnv = serverGroup.accountDetails && serverGroup.accountDetails.environment === 'test';
              const shouldAutoEnableIPv6 =
                asgSettings && asgSettings.enableIPv6 && asgSettings.setIPv6InTest && isTestEnv;

              angular.extend(command, {
                iamRole: launchTemplateData.iamInstanceProfile.name,
                keyPair: launchTemplateData.keyName,
                associateIPv6Address: shouldAutoEnableIPv6 || Boolean(ipv6AddressCount),
                ramdiskId: launchTemplateData.ramdiskId,
                instanceMonitoring: launchTemplateData.monitoring && launchTemplateData.monitoring.enabled,
                ebsOptimized: launchTemplateData.ebsOptimized,
                spotPrice: spotMaxPrice || undefined,
                requireIMDSv2: Boolean(launchTemplateData.metadataOptions?.httpTokens === 'required'),
                unlimitedCpuCredits: launchTemplateData.creditSpecification
                  ? launchTemplateData.creditSpecification.cpuCredits === 'unlimited'
                  : undefined,
              });

              command.viewState.imageId = launchTemplateData.imageId;
            }

            if (mode === 'clone' && serverGroup.image && serverGroup.image.name) {
              command.amiName = serverGroup.image.name;
            }

            if (serverGroup.launchConfig && serverGroup.launchConfig.securityGroups.length) {
              command.securityGroups = serverGroup.launchConfig.securityGroups;
            }

            if (serverGroup.launchTemplate && serverGroup.launchTemplate.launchTemplateData.securityGroups.length) {
              command.securityGroups = serverGroup.launchTemplate.launchTemplateData.securityGroups;
            }

            if (serverGroup.launchTemplate && serverGroup.launchTemplate.launchTemplateData.networkInterfaces) {
              const networkInterface =
                serverGroup.launchTemplate.launchTemplateData.networkInterfaces.find((ni) => ni.deviceIndex === 0) ??
                ({} as INetworkInterface);
              command.securityGroups = networkInterface.groups;
            }

            return command;
          });
      }

      // Since Deck allows changing priority of instance types via drag handle, fill priority field explicitly if empty
      function getInstanceTypesWithPriority(
        instanceTypeOverrides: IAmazonLaunchTemplateOverrides[],
      ): IAmazonInstanceTypeOverride[] {
        let explicitPriority = 1;
        return _.sortBy(instanceTypeOverrides, ['priority']).map((override) => {
          const { instanceType, weightedCapacity } = override;
          let priority;
          if (override.priority) {
            priority = override.priority;
            explicitPriority = override.priority + 1;
          } else {
            priority = explicitPriority++;
          }
          return { instanceType, weightedCapacity, priority };
        });
      }

      function isSimpleModeEnabled(
        command: IAmazonServerGroupDeployConfiguration | Partial<IAmazonServerGroupCommand>,
      ) {
        const isAdvancedModeEnabledInCommand =
          command.onDemandAllocationStrategy ||
          command.onDemandBaseCapacity ||
          command.onDemandPercentageAboveBaseCapacity ||
          command.spotAllocationStrategy ||
          command.spotInstancePools ||
          (command.launchTemplateOverridesForInstanceType && command.launchTemplateOverridesForInstanceType.length > 0);

        return !isAdvancedModeEnabledInCommand;
      }

      return {
        buildNewServerGroupCommand,
        buildServerGroupCommandFromExisting,
        buildNewServerGroupCommandForPipeline,
        buildServerGroupCommandFromPipeline,
        buildUpdateServerGroupCommand,
      } as AwsServerGroupCommandBuilder;
    },
  ]);
