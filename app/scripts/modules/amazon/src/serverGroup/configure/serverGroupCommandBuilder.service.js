import * as angular from 'angular';
import _ from 'lodash';

import {
  AccountService,
  DeploymentStrategyRegistry,
  INSTANCE_TYPE_SERVICE,
  NameUtils,
  SubnetReader,
} from '@spinnaker/core';
import { AWSProviderSettings } from '../../aws.settings';

import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

export const AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE =
  'spinnaker.amazon.serverGroupCommandBuilder.service';
export const name = AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE; // for backwards compatibility
angular
  .module(AMAZON_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE, [
    INSTANCE_TYPE_SERVICE,
    AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .factory('awsServerGroupCommandBuilder', [
    '$q',
    'instanceTypeService',
    'awsServerGroupConfigurationService',
    function ($q, instanceTypeService, awsServerGroupConfigurationService) {
      function buildNewServerGroupCommand(application, defaults) {
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
            const applicationAwsSettings = _.get(application, 'attributes.providerSettings.aws', {});

            let defaultIamRole = AWSProviderSettings.defaults.iamRole || 'BaseIAMRole';
            defaultIamRole = defaultIamRole.replace('{{application}}', application.name);

            const useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

            const command = {
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
                useAllImageSelection: false,
                useSimpleCapacity: true,
                usePreferredZones: true,
                mode: defaults.mode || 'create',
                disableStrategySelection: true,
                dirty: {},
                submitButtonLabel: getSubmitButtonLabel(defaults.mode || 'create'),
              },
            };

            if (
              application.attributes &&
              application.attributes.platformHealthOnlyShowOverride &&
              application.attributes.platformHealthOnly
            ) {
              command.interestingHealthProviderNames = ['Amazon'];
            }

            if (
              defaultCredentials === 'test' &&
              AWSProviderSettings.serverGroups &&
              AWSProviderSettings.serverGroups.enableIPv6
            ) {
              command.associateIPv6Address = true;
            }

            if (AWSProviderSettings.serverGroups && AWSProviderSettings.serverGroups.enableIMDSv2) {
              /**
               * Older SDKs do not support IMDSv2. A timestamp can be optionally configured at which any apps created after can safely default to using IMDSv2.
               */
              const appAgeRequirement = AWSProviderSettings.serverGroups.defaultIMDSv2AppAgeLimit;
              const creationDate = application.attributes && application.attributes.createTs;

              command.requireIMDSv2 =
                appAgeRequirement && creationDate && Number(creationDate) > appAgeRequirement ? true : false;
            }

            return command;
          });
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster) {
        const pipelineCluster = _.cloneDeep(originalCluster);
        const region = Object.keys(pipelineCluster.availabilityZones)[0];
        const instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType(
          'aws',
          pipelineCluster.instanceType,
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
          },
        });
      }

      function getSubmitButtonLabel(mode) {
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

      function buildUpdateServerGroupCommand(serverGroup) {
        const command = {
          type: 'modifyAsg',
          asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
          cooldown: serverGroup.asg.defaultCooldown,
          enabledMetrics: _.get(serverGroup, 'asg.enabledMetrics', []).map((m) => m.metric),
          healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
          healthCheckType: serverGroup.asg.healthCheckType,
          terminationPolicies: angular.copy(serverGroup.asg.terminationPolicies),
          credentials: serverGroup.account,
          capacityRebalance: serverGroup.asg.capacityRebalance,
        };
        awsServerGroupConfigurationService.configureUpdateCommand(command);
        return command;
      }

      function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
        const preferredZonesLoader = AccountService.getPreferredZonesByAccount('aws');
        const subnetsLoader = SubnetReader.listSubnets();

        const serverGroupName = NameUtils.parseServerGroupName(serverGroup.asg.autoScalingGroupName);

        const instanceType = serverGroup.launchConfig
          ? serverGroup.launchConfig.instanceType
          : serverGroup.launchTemplate
          ? serverGroup.launchTemplate.launchTemplateData.instanceType
          : null;
        const instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('aws', instanceType);

        return $q
          .all([preferredZonesLoader, subnetsLoader, instanceTypeCategoryLoader])
          .then(function ([preferredZones, subnets, instanceProfile]) {
            const zones = serverGroup.asg.availabilityZones.sort();
            let usePreferredZones = false;
            const preferredZonesForAccount = preferredZones[serverGroup.account];
            if (preferredZonesForAccount) {
              const preferredZones = preferredZonesForAccount[serverGroup.region].sort();
              usePreferredZones = zones.join(',') === preferredZones.join(',');
            }

            // These processes should never be copied over, as the affect launching instances and enabling traffic
            const enabledProcesses = ['Launch', 'Terminate', 'AddToLoadBalancer'];

            const applicationAwsSettings = _.get(application, 'attributes.providerSettings.aws', {});
            const useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

            const existingTags = {};
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

            const command = {
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
              },
            };

            if (
              application.attributes &&
              application.attributes.platformHealthOnlyShowOverride &&
              application.attributes.platformHealthOnly
            ) {
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
              const subnet = _.chain(subnets).find({ id: subnetId }).value();
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
                instanceMonitoring: serverGroup.launchConfig.instanceMonitoring.enabled,
                ebsOptimized: serverGroup.launchConfig.ebsOptimized,
                spotPrice: serverGroup.launchConfig.spotPrice,
              });
              if (serverGroup.launchConfig.userData) {
                command.base64UserData = serverGroup.launchConfig.userData;
              }
              command.viewState.imageId = serverGroup.launchConfig.imageId;
            }

            if (serverGroup.launchTemplate) {
              const { launchTemplateData } = serverGroup.launchTemplate;
              const maxPrice =
                launchTemplateData.instanceMarketOptions &&
                launchTemplateData.instanceMarketOptions.spotOptions &&
                launchTemplateData.instanceMarketOptions.spotOptions.maxPrice;
              const { ipv6AddressCount } =
                launchTemplateData.networkInterfaces &&
                launchTemplateData.networkInterfaces.length &&
                launchTemplateData.networkInterfaces[0];

              const asgSettings = AWSProviderSettings.serverGroups;
              const isTestEnv = serverGroup.accountDetails && serverGroup.accountDetails.environment === 'test';
              const shouldAutoEnableIPv6 =
                asgSettings && asgSettings.enableIPv6 && asgSettings.setIPv6InTest && isTestEnv;

              angular.extend(command, {
                instanceType: launchTemplateData.instanceType,
                iamRole: launchTemplateData.iamInstanceProfile.name,
                keyPair: launchTemplateData.keyName,
                associateIPv6Address: shouldAutoEnableIPv6 || Boolean(ipv6AddressCount),
                ramdiskId: launchTemplateData.ramdiskId,
                instanceMonitoring: launchTemplateData.monitoring.enabled,
                ebsOptimized: launchTemplateData.ebsOptimized,
                spotPrice: maxPrice || undefined,
                requireIMDSv2: Boolean(
                  launchTemplateData.metadataOptions && launchTemplateData.metadataOptions.httpsTokens === 'required',
                ),
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
                serverGroup.launchTemplate.launchTemplateData.networkInterfaces.find((ni) => ni.deviceIndex === 0) ||
                {};
              command.securityGroups = networkInterface.groups;
            }

            return command;
          });
      }

      return {
        buildNewServerGroupCommand: buildNewServerGroupCommand,
        buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
        buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
        buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
        buildUpdateServerGroupCommand: buildUpdateServerGroupCommand,
      };
    },
  ]);
