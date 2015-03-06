'use strict';


angular.module('deckApp.gce.serverGroupCommandBuilder.service', [
  'restangular',
  'deckApp.settings',
  'deckApp.account.service',
  'deckApp.naming',
  'deckApp.gce.instanceType.service',
])
  .factory('gceServerGroupCommandBuilder', function (settings, Restangular, $exceptionHandler, $q, accountService, gceInstanceTypeService, namingService) {

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

    function populateCustomMetadata(userData, command) {
      if (userData) {
        // userData is a base64-encoded json object.
        var userDataObj = JSON.parse(window.atob(userData));

        for (var key in userDataObj) {
          // Don't show 'load-balancer-names' key/value pair in the wizard.
          if (key !== 'load-balancer-names') {
            // The 'key' and 'value' attributes are used to enable the Add/Remove behavior in the wizard.
            command.instanceMetadata.push({key: key, value: userDataObj[key]});
          }
        }
      }
    }

    function buildNewServerGroupCommand(application) {

    // TODO(duftler): Fetch default account from settings once it's refactored to support defaults for multiple providers.
    return {
      application: application.name,
      credentials: application.accounts.length > 0 ? application.accounts[0] : 'my-account-name',
      region: 'us-central1',
      capacity: {
        min: 0,
        max: 0,
        desired: 1
      },
      instanceMetadata: [],
      cooldown: 10,
      healthCheckType: 'EC2',
      healthCheckGracePeriod: 600,
      instanceMonitoring: false,
      ebsOptimized: false,
      providerType: 'gce',
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

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);

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
        instanceMetadata: [],
        availabilityZones: serverGroup.asg.availabilityZones,
        providerType: 'gce',
        selectedProvider: 'gce',
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
          serverGroupName: serverGroup.name,
          asgName: serverGroup.name
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
        populateCustomMetadata(serverGroup.launchConfig.userData, command);
      }

      return command;
    }

    function buildSubmittableCommand(original) {
      var command = angular.copy(original);
      var transformedInstanceMetadata = {};
      // The instanceMetadata is stored using 'key' and 'value' attributes to enable the Add/Remove behavior in the wizard.
      command.instanceMetadata.forEach(function(metadataPair) {
        transformedInstanceMetadata[metadataPair.key] = metadataPair.value;
      });

      // We use this list of load balancer names when 'Enabling' a server group.
      if (command.loadBalancers && command.loadBalancers.length > 0) {
        transformedInstanceMetadata['load-balancer-names'] = command.loadBalancers.toString();
      }
      command.instanceMetadata = transformedInstanceMetadata;
      return command;
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildSubmittableCommand: buildSubmittableCommand
    };
});

