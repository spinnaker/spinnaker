'use strict';


angular.module('deckApp')
  .factory('awsServerGroupService', function (settings, Restangular, $exceptionHandler, $q, accountService, mortService) {
    function buildNewServerGroupCommand(application) {
      var preferredZonesLoader = accountService.getPreferredZonesByAccount();
      var regionsKeyedByAccountLoader = accountService.getRegionsKeyedByAccount();
      var asyncLoader = $q.all({preferredZones: preferredZonesLoader, regionsKeyedByAccount: regionsKeyedByAccountLoader});

      return asyncLoader.then(function(asyncData) {
        var defaultCredentials = settings.defaults.account;
        var defaultRegion = settings.defaults.region;

        var defaultZones = asyncData.preferredZones[defaultCredentials];
        if (!defaultZones) {
          defaultZones = asyncData.preferredZones["default"];
        }
        var availabilityZones = defaultZones[defaultRegion];
        var regions = asyncData.regionsKeyedByAccount[defaultCredentials];
        var keyPair = regions ? regions.defaultKeyPair : null;

        return {
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
          capacity: {
            min: 1,
            max: 1,
            desired: 1
          },
          cooldown: 10,
          healthCheckType: 'EC2',
          healthCheckGracePeriod: 600,
          instanceMonitoring: false,
          ebsOptimized: false,
          selectedProvider: 'aws',
          iamRole: 'BaseIAMRole', // should not be hard coded here

          terminationPolicies: ['Default'],
          vpcId: null,
          availabilityZones: availabilityZones,
          keyPair: keyPair,
          suspendedProcesses: [],
          viewState: {
            instanceProfile: null,
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: true,
            usePreferredZones: true,
            mode: 'create',
            isNew: true,
          },
        };
      });
    }

    function buildServerGroupCommandFromPipeline(application, cluster, account) {

      return buildNewServerGroupCommand(application).then(function(command) {

        var region = Object.keys(cluster.availabilityZones)[0];
        var zones = cluster.availabilityZones[region];
        var usePreferredZones = zones.join(',') === command.availabilityZones.join(',');

        var viewState = {
          instanceProfile: 'custom',
          disableImageSelection: true,
          useSimpleCapacity: cluster.capacity.minSize === cluster.capacity.maxSize,
          usePreferredZones: usePreferredZones,
          mode: 'clone',
          isNew: false,
        };

        var viewOverrides = {
          credentials: account,
          availabilityZones: cluster.availabilityZones[command.region],
          viewState: viewState,
        };

        cluster.strategy = cluster.strategy || '';

        return angular.extend({}, command, cluster, viewOverrides);
      });

    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode, parseServerGroupName) {
      mode = mode || 'clone';
      var preferredZonesLoader = accountService.getPreferredZonesByAccount();
      var subnetsLoader = mortService.listSubnets();
      var asyncLoader = $q.all({preferredZones: preferredZonesLoader, subnets: subnetsLoader});

      return asyncLoader.then(function(asyncData) {
        var serverGroupName = parseServerGroupName(serverGroup.asg.autoScalingGroupName);

        var zones = serverGroup.asg.availabilityZones.sort();
        var preferredZones = asyncData.preferredZones[serverGroup.account][serverGroup.region].sort();
        var usePreferredZones = zones.join(',') === preferredZones.join(',');

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
          capacity: {
            'min': serverGroup.asg.minSize,
            'max': serverGroup.asg.maxSize,
            'desired': serverGroup.asg.desiredCapacity
          },
          availabilityZones: zones,
          selectedProvider: 'aws',
          source: {
            account: serverGroup.account,
            region: serverGroup.region,
            asgName: serverGroup.asg.autoScalingGroupName,
          },
          suspendedProcesses: _.pluck(serverGroup.asg.suspendedProcesses, 'processName'),
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
            usePreferredZones: usePreferredZones,
            mode: mode,
            isNew: false,
          },
        };

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
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

