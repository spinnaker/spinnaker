'use strict';


angular.module('deckApp')
  .factory('gceServerGroupService', function (settings, Restangular, $exceptionHandler, $q, accountService, mortService, gceInstanceTypeService) {

    // Two assumptions here:
    //   1) All GCE machine types are represented in the tree of choices.
    //   2) Each machine type appears in exactly one category.
    function determineInstanceCategoryFromInstanceType(command) {
      gceInstanceTypeService.getCategories().then(function(categories) {
        categories.forEach(function(category) {
          category.families.forEach(function(family) {
            family.instanceTypes.forEach(function(instanceType) {
              if (instanceType.name === command.instanceType) {
                command.viewState.instanceProfile = category.type;
              }
            });
          });
        });
      });
    }

    function buildNewServerGroupCommand(application) {
      // TODO(duftler): Load regions, zones, ...

    return {
      application: application.name,
      credentials: 'my-account-name',
      region: 'us-central1',
      capacity: {
        min: 0,
        max: 0,
        desired: 1
      },
      cooldown: 10,
      healthCheckType: 'EC2',
      healthCheckGracePeriod: 600,
      instanceMonitoring: false,
      ebsOptimized: false,
      selectedProvider: 'gce',
      iamRole: 'BaseIAMRole',       // should not be hard coded here

      terminationPolicies: ['Default'],
      vpcId: null,
      availabilityZones: [],
      keyPair: 'nf-test-keypair-a', // should not be hard coded here
      viewState: {
        instanceProfile: null,
        allImageSelection: null,
        useAllImageSelection: false,
        useSimpleCapacity: true,
        usePreferredZones: true,
        mode: 'create',
      },
    };
  }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode, parseServerGroupName) {
      // TODO(duftler): Load regions, zones, ...

      mode = mode || 'clone';

      var serverGroupName = parseServerGroupName(serverGroup.name);

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
          min: serverGroup.asg.minSize,
          max: serverGroup.asg.maxSize,
          desired: serverGroup.asg.desiredCapacity
        },
        zone: serverGroup.zones[0],
        availabilityZones: serverGroup.asg.availabilityZones,
        selectedProvider: 'gce',
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
          serverGroupName: serverGroup.name
        },
        viewState: {
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: serverGroup.asg.minSize === serverGroup.asg.maxSize,
          usePreferredZones: false,
          mode: mode,
        },
      };

      if (serverGroup.launchConfig) {
        angular.extend(command, {
          instanceType: serverGroup.launchConfig.instanceType,
          iamRole: serverGroup.launchConfig.iamInstanceProfile,
          keyPair: serverGroup.launchConfig.keyName,
          associatePublicIpAddress: serverGroup.launchConfig.associatePublicIpAddress,
          ramdiskId: serverGroup.launchConfig.ramdiskId,
          instanceMonitoring: serverGroup.launchConfig.instanceMonitoring && serverGroup.launchConfig.instanceMonitoring.enabled,
          ebsOptimized: serverGroup.launchConfig.ebsOptimized,
        });
        command.viewState.imageId = serverGroup.launchConfig.imageId;
        determineInstanceCategoryFromInstanceType(command);
      }

      return command;
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting
    };
});

