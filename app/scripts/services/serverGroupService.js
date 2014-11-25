'use strict';


angular.module('deckApp')
  .factory('serverGroupService', function (settings, Restangular, $exceptionHandler, $q, accountService, mortService) {

    var oortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function getServerGroupEndpoint(application, account, clusterName, serverGroupName) {
      return oortEndpoint.one('applications', application).all('clusters').all(account).all(clusterName).one('aws').one('serverGroups', serverGroupName);
    }

    function getServerGroup(application, account, region, serverGroupName) {
      return oortEndpoint.one('applications', application).all('serverGroups').all(account).all(region).one(serverGroupName).get();
    }

    function getScalingActivities(application, account, clusterName, serverGroupName, region) {
      return getServerGroupEndpoint(application, account, clusterName, serverGroupName).all('scalingActivities').getList({region: region}).then(function(activities) {
        return activities;
      },
      function(error) {
        $exceptionHandler(error, 'error retrieving scaling activities');
        return [];
      });
    }

    function parseServerGroupName(serverGroupName) {
      var versionPattern = /(v\d{3})/;
      if (!serverGroupName) {
        return {};
      }
      var split = serverGroupName.split('-'),
          isVersioned = versionPattern.test(split[split.length - 1]),
          result = {
            application: split[0],
            stack: '',
            freeFormDetails: ''
          };

      // get rid of version, since we are not returning it
      if (isVersioned) {
        split.pop();
      }

      if (split.length > 1) {
        result.stack = split[1];
      }
      if (split.length > 2) {
        result.freeFormDetails = split.slice(2, split.length).join('-');
      }

      return result;
    }

    function getClusterName(app, cluster, detail) {
      var clusterName = app;
      if (cluster) {
        clusterName += '-' + cluster;
      }
      if (!cluster && detail) {
        clusterName += '-';
      }
      if (detail) {
        clusterName += '-' + detail;
      }
      return clusterName;
    }

    function buildNewServerGroupCommand(application, provider) {
      var preferredZonesLoader = accountService.getPreferredZonesByAccount();
      var regionsKeyedByAccountLoader = accountService.getRegionsKeyedByAccount();
      var asyncLoader = $q.all({preferredZones: preferredZonesLoader, regionsKeyedByAccount: regionsKeyedByAccountLoader});

      return asyncLoader.then(function(asyncData) {
        var defaultCredentials = settings.defaults.account;
        var defaultRegion = settings.defaults.region;

        var availabilityZones = asyncData.preferredZones[defaultCredentials][defaultRegion];
        var keyPair = asyncData.regionsKeyedByAccount[defaultCredentials].defaultKeyPair;

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
          selectedProvider: provider,
          iamRole: 'BaseIAMRole', // should not be hard coded here

          terminationPolicies: ['Default'],
          vpcId: null,
          availabilityZones: availabilityZones,
          keyPair: keyPair,
          viewState: {
            instanceProfile: null,
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: true,
            usePreferredZones: true,
            mode: 'create',
          },
        };
      });
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
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
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
            usePreferredZones: usePreferredZones,
            mode: mode,
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
      getScalingActivities: getScalingActivities,
      parseServerGroupName: parseServerGroupName,
      getClusterName: getClusterName,
      getServerGroup: getServerGroup,
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting
    };
});

