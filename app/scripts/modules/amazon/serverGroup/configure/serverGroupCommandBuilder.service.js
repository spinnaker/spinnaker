'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroupCommandBuilder.service', [
  require('../../../core/account/account.service.js'),
  require('../../../core/subnet/subnet.read.service.js'),
  require('../../../core/instance/instanceTypeService.js'),
  require('../../../core/naming/naming.service.js'),
  require('./serverGroupConfiguration.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('awsServerGroupCommandBuilder', function (settings, $q,
                                                     accountService, subnetReader, namingService, instanceTypeService,
                                                     awsServerGroupConfigurationService, _) {

    function buildNewServerGroupCommand (application, defaults) {
      defaults = defaults || {};
      var credentialsLoader = accountService.getCredentialsKeyedByAccount('aws');

      var defaultCredentials = defaults.account || application.defaultCredentials.aws || settings.providers.aws.defaults.account;
      var defaultRegion = defaults.region || application.defaultRegions.aws || settings.providers.aws.defaults.region;

      var preferredZonesLoader = accountService.getAvailabilityZonesForAccountAndRegion('aws', defaultCredentials, defaultRegion);

      return $q.all({
        preferredZones: preferredZonesLoader,
        credentialsKeyedByAccount: credentialsLoader,
      })
        .then(function (asyncData) {
          var availabilityZones = asyncData.preferredZones;

          var credentials = asyncData.credentialsKeyedByAccount[defaultCredentials];
          var keyPair = credentials ? credentials.defaultKeyPair : null;
          var applicationAwsSettings = _.get(application, 'attributes.providerSettings.aws', {});

          var defaultIamRole = settings.providers.aws.defaults.iamRole || 'BaseIAMRole';
          defaultIamRole = defaultIamRole.replace('{{application}}', application.name);

          var command = {
            application: application.name,
            credentials: defaultCredentials,
            region: defaultRegion,
            strategy: '',
            capacity: {
              min: 1,
              max: 1,
              desired: 1
            },
            targetHealthyDeployPercentage: 100,
            cooldown: 10,
            healthCheckType: 'EC2',
            healthCheckGracePeriod: 600,
            instanceMonitoring: false,
            ebsOptimized: false,
            selectedProvider: 'aws',
            iamRole: defaultIamRole,
            terminationPolicies: ['Default'],
            vpcId: null,
            availabilityZones: availabilityZones,
            keyPair: keyPair,
            suspendedProcesses: [],
            securityGroups: [],
            tags: {},
            useAmiBlockDeviceMappings: applicationAwsSettings.useAmiBlockDeviceMappings || false,
            viewState: {
              instanceProfile: 'custom',
              useAllImageSelection: false,
              useSimpleCapacity: true,
              usePreferredZones: true,
              mode: defaults.mode || 'create',
              disableStrategySelection: true,
              dirty: {},
            },
          };

          return command;
        });
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('aws', pipelineCluster.instanceType);
      var commandOptions = { account: pipelineCluster.account, region: region };
      var asyncLoader = $q.all({command: buildNewServerGroupCommand(application, commandOptions), instanceProfile: instanceTypeCategoryLoader});

      return asyncLoader.then(function(asyncData) {
        var command = asyncData.command;
        var zones = pipelineCluster.availabilityZones[region];
        var usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

        var viewState = {
          instanceProfile: asyncData.instanceProfile,
          disableImageSelection: true,
          useSimpleCapacity: pipelineCluster.capacity.min === pipelineCluster.capacity.max && pipelineCluster.useSourceCapacity !== true,
          usePreferredZones: usePreferredZones,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
          templatingEnabled: true,
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
        }
      });
    }

    function buildUpdateServerGroupCommand(serverGroup) {
      var command = {
        type: 'modifyAsg',
        asgs: [
          { asgName: serverGroup.name, region: serverGroup.region }
        ],
        cooldown: serverGroup.asg.defaultCooldown,
        healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
        healthCheckType: serverGroup.asg.healthCheckType,
        terminationPolicies: angular.copy(serverGroup.asg.terminationPolicies),
        credentials: serverGroup.account
      };
      awsServerGroupConfigurationService.configureUpdateCommand(command);
      return command;
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode = 'clone') {
      var preferredZonesLoader = accountService.getPreferredZonesByAccount('aws');
      var subnetsLoader = subnetReader.listSubnets();

      var serverGroupName = namingService.parseServerGroupName(serverGroup.asg.autoScalingGroupName);

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

        var command = {
          application: application.name,
          strategy: '',
          stack: serverGroupName.stack,
          freeFormDetails: serverGroupName.freeFormDetails,
          credentials: serverGroup.account,
          cooldown: serverGroup.asg.defaultCooldown,
          healthCheckGracePeriod: serverGroup.asg.healthCheckGracePeriod,
          healthCheckType: serverGroup.asg.healthCheckType,
          terminationPolicies: serverGroup.asg.terminationPolicies,
          loadBalancers: serverGroup.asg.loadBalancerNames,
          region: serverGroup.region,
          useSourceCapacity: false,
          capacity: {
            'min': serverGroup.asg.minSize,
            'max': serverGroup.asg.maxSize,
            'desired': serverGroup.asg.desiredCapacity
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
            .filter((name) => enabledProcesses.indexOf(name) < 0),
          tags: serverGroup.tags || {},
          useAmiBlockDeviceMappings: applicationAwsSettings.useAmiBlockDeviceMappings || false,
          viewState: {
            instanceProfile: asyncData.instanceProfile,
            useAllImageSelection: false,
            useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
            usePreferredZones: usePreferredZones,
            mode: mode,
            isNew: false,
            dirty: {},
          },
        };

        if (mode === 'clone') {
          command.useSourceCapacity = true;
          command.viewState.useSimpleCapacity = false;
        }

        if (mode === 'editPipeline') {
          command.strategy = 'redblack';
          command.suspendedProcesses = [];
        }

        var vpcZoneIdentifier = serverGroup.asg.vpczoneIdentifier;
        if (vpcZoneIdentifier !== '') {
          var subnetId = vpcZoneIdentifier.split(',')[0];
          var subnet = _(asyncData.subnets).find({'id': subnetId});
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
          });
          if (serverGroup.launchConfig.userData) {
            command.base64UserData = serverGroup.launchConfig.userData;
          }
          command.viewState.imageId = serverGroup.launchConfig.imageId;
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
});
