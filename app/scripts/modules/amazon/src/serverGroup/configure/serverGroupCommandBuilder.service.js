'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, INSTANCE_TYPE_SERVICE, NameUtils, SubnetReader } from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';

module.exports = angular
  .module('spinnaker.amazon.serverGroupCommandBuilder.service', [
    INSTANCE_TYPE_SERVICE,
    AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .factory('awsServerGroupCommandBuilder', [
    '$q',
    'instanceTypeService',
    'awsServerGroupConfigurationService',
    function($q, instanceTypeService, awsServerGroupConfigurationService) {
      function buildNewServerGroupCommand(application, defaults) {
        defaults = defaults || {};
        var credentialsLoader = AccountService.getCredentialsKeyedByAccount('aws');

        var defaultCredentials =
          defaults.account || application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
        var defaultRegion = defaults.region || application.defaultRegions.aws || AWSProviderSettings.defaults.region;
        var defaultSubnet = defaults.subnet || AWSProviderSettings.defaults.subnetType || '';

        var preferredZonesLoader = AccountService.getAvailabilityZonesForAccountAndRegion(
          'aws',
          defaultCredentials,
          defaultRegion,
        );

        return $q
          .all({
            preferredZones: preferredZonesLoader,
            credentialsKeyedByAccount: credentialsLoader,
          })
          .then(function(asyncData) {
            var availabilityZones = asyncData.preferredZones;

            var credentials = asyncData.credentialsKeyedByAccount[defaultCredentials];
            var keyPair = credentials ? credentials.defaultKeyPair : null;
            var applicationAwsSettings = _.get(application, 'attributes.providerSettings.aws', {});

            var defaultIamRole = AWSProviderSettings.defaults.iamRole || 'BaseIAMRole';
            defaultIamRole = defaultIamRole.replace('{{application}}', application.name);

            var useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

            var command = {
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
              availabilityZones: availabilityZones,
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

            return command;
          });
      }

      function buildServerGroupCommandFromPipeline(application, originalCluster) {
        var pipelineCluster = _.cloneDeep(originalCluster);
        var region = Object.keys(pipelineCluster.availabilityZones)[0];
        var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType(
          'aws',
          pipelineCluster.instanceType,
        );
        var commandOptions = { account: pipelineCluster.account, region: region };
        var asyncLoader = $q.all({
          command: buildNewServerGroupCommand(application, commandOptions),
          instanceProfile: instanceTypeCategoryLoader,
        });

        return asyncLoader.then(function(asyncData) {
          var command = asyncData.command;
          var zones = pipelineCluster.availabilityZones[region];
          var usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

          var viewState = {
            instanceProfile: asyncData.instanceProfile,
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

          var viewOverrides = {
            region: region,
            credentials: pipelineCluster.account,
            availabilityZones: pipelineCluster.availabilityZones[region],
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
        var command = {
          type: 'modifyAsg',
          asgs: [{ asgName: serverGroup.name, region: serverGroup.region }],
          cooldown: serverGroup.asg.defaultCooldown,
          enabledMetrics: _.get(serverGroup, 'asg.enabledMetrics', []).map(m => m.metric),
          healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
          healthCheckType: serverGroup.asg.healthCheckType,
          terminationPolicies: angular.copy(serverGroup.asg.terminationPolicies),
          credentials: serverGroup.account,
        };
        awsServerGroupConfigurationService.configureUpdateCommand(command);
        return command;
      }

      function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
        var preferredZonesLoader = AccountService.getPreferredZonesByAccount('aws');
        var subnetsLoader = SubnetReader.listSubnets();

        var serverGroupName = NameUtils.parseServerGroupName(serverGroup.asg.autoScalingGroupName);

        var instanceType = serverGroup.launchConfig ? serverGroup.launchConfig.instanceType : null;
        var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('aws', instanceType);

        var asyncLoader = $q.all({
          preferredZones: preferredZonesLoader,
          subnets: subnetsLoader,
          instanceProfile: instanceTypeCategoryLoader,
        });

        return asyncLoader.then(function(asyncData) {
          var zones = serverGroup.asg.availabilityZones.sort();
          var usePreferredZones = false;
          var preferredZonesForAccount = asyncData.preferredZones[serverGroup.account];
          if (preferredZonesForAccount) {
            var preferredZones = preferredZonesForAccount[serverGroup.region].sort();
            usePreferredZones = zones.join(',') === preferredZones.join(',');
          }

          // These processes should never be copied over, as the affect launching instances and enabling traffic
          let enabledProcesses = ['Launch', 'Terminate', 'AddToLoadBalancer'];

          var applicationAwsSettings = _.get(application, 'attributes.providerSettings.aws', {});
          var useAmiBlockDeviceMappings = applicationAwsSettings.useAmiBlockDeviceMappings || false;

          const existingTags = {};
          // These tags are applied by Clouddriver (if configured to do so), regardless of what the user might enter
          // Might be worth feature flagging this if it turns out other folks are hard-coding these values
          const reservedTags = ['spinnaker:application', 'spinnaker:stack', 'spinnaker:details'];
          if (serverGroup.asg.tags) {
            serverGroup.asg.tags
              .filter(t => !reservedTags.includes(t.key))
              .forEach(tag => {
                existingTags[tag.key] = tag.value;
              });
          }

          var command = {
            application: application.name,
            strategy: '',
            stack: serverGroupName.stack,
            freeFormDetails: serverGroupName.freeFormDetails,
            credentials: serverGroup.account,
            cooldown: serverGroup.asg.defaultCooldown,
            enabledMetrics: _.get(serverGroup, 'asg.enabledMetrics', []).map(m => m.metric),
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
              .map(process => process.processName)
              .filter(name => !enabledProcesses.includes(name)),
            tags: Object.assign({}, serverGroup.tags, existingTags),
            targetGroups: serverGroup.targetGroups,
            useAmiBlockDeviceMappings: useAmiBlockDeviceMappings,
            copySourceCustomBlockDeviceMappings: mode === 'clone', // default to using block device mappings if not cloning
            viewState: {
              instanceProfile: asyncData.instanceProfile,
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
            command.suspendedProcesses = [];
          }

          var vpcZoneIdentifier = serverGroup.asg.vpczoneIdentifier;
          if (vpcZoneIdentifier !== '') {
            var subnetId = vpcZoneIdentifier.split(',')[0];
            var subnet = _.chain(asyncData.subnets)
              .find({ id: subnetId })
              .value();
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

          if (mode === 'clone' && serverGroup.image && serverGroup.image.name) {
            command.amiName = serverGroup.image.name;
          }

          if (serverGroup.launchConfig && serverGroup.launchConfig.securityGroups.length) {
            command.securityGroups = serverGroup.launchConfig.securityGroups;
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
