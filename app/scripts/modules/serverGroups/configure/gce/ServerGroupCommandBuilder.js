'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.serverGroupCommandBuilder.service', [
  require('restangular'),
  require('../../../caches/deckCacheFactory.js'),
  require('../../../account/accountService.js'),
  require('../../../naming/naming.service.js'),
  require('../../../../services/instanceTypeService.js'),
  require('../../../../services/gceInstanceTypeService.js'),
  require('utils/lodash.js'),
])
  .factory('gceServerGroupCommandBuilder', function (settings, Restangular, $exceptionHandler, $q,
                                                     accountService, instanceTypeService, gceInstanceTypeService, namingService, _) {

    // Two assumptions here:
    //   1) All GCE machine types are represented in the tree of choices.
    //   2) Each machine type appears in exactly one category.
    function determineInstanceCategoryFromInstanceType(command) {
      return gceInstanceTypeService.getCategories().then(function(categories) {
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

    function populateTags(instanceTemplateTags, command) {
      if (instanceTemplateTags && instanceTemplateTags.items) {
        _.map(instanceTemplateTags.items, function(tag) {
          command.tags.push({value: tag});
        });
      }
    }

    function attemptToSetValidCredentials(application, defaultCredentials, command) {
      return accountService.listAccounts('gce').then(function(gceAccounts) {
        var gceAccountNames = _.pluck(gceAccounts, 'name');
        var firstGCEAccount = null;

        if (application.accounts.length) {
          firstGCEAccount = _.find(application.accounts, function (applicationAccount) {
            return gceAccountNames.indexOf(applicationAccount) !== -1;
          });
        }

        var defaultCredentialsAreValid = defaultCredentials && gceAccountNames.indexOf(defaultCredentials) !== -1;

        command.credentials =
          defaultCredentialsAreValid ? defaultCredentials : (firstGCEAccount ? firstGCEAccount : 'my-account-name');
      });
    }

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || settings.providers.gce.defaults.account;
      var defaultRegion = defaults.region || settings.providers.gce.defaults.region;

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        strategy: '',
        capacity: {
          min: 0,
          max: 0,
          desired: 1
        },
        instanceMetadata: [],
        tags: [],
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
          mode: defaults.mode || 'create',
        }
      };

      attemptToSetValidCredentials(application, defaultCredentials, command);

      return $q.when(command);
  }

    // Only used to prepare view requiring template selecting
    function buildNewServerGroupCommandForPipeline() {
      return $q.when({
        viewState: {
          requiresTemplateSelection: true,
        }
      });
    }

    function buildServerGroupCommandFromExisting(application, serverGroup, mode) {
      mode = mode || 'clone';

      var serverGroupName = namingService.parseServerGroupName(serverGroup.name);

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
          min: serverGroup.asg.minSize,
          max: serverGroup.asg.maxSize,
          desired: serverGroup.asg.desiredCapacity
        },
        zone: serverGroup.zones[0],
        instanceMetadata: [],
        tags: [],
        availabilityZones: [],
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
          useSimpleCapacity: true,
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
        return determineInstanceCategoryFromInstanceType(command).then(function() {
          populateCustomMetadata(serverGroup.launchConfig.userData, command);
          populateTags(serverGroup.launchConfig.instanceTemplate.properties.tags, command);
          return command;
        });
      }

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, pipelineCluster) {

      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('gce', pipelineCluster.instanceType);
      var commandOptions = { account: pipelineCluster.account, region: region };
      var asyncLoader = $q.all({command: buildNewServerGroupCommand(application, commandOptions), instanceProfile: instanceTypeCategoryLoader});

      return asyncLoader.then(function(asyncData) {
        var command = asyncData.command;

        var viewState = {
          instanceProfile: asyncData.instanceProfile,
          disableImageSelection: true,
          useSimpleCapacity: true,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
        };

        var viewOverrides = {
          region: region,
          credentials: pipelineCluster.account,
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';

        return angular.extend({}, command, pipelineCluster, viewOverrides);
      });

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

      var transformedTags = [];
      // The tags are stored using a 'value' attribute to enable the Add/Remove behavior in the wizard.
      command.tags.forEach(function(tag) {
        transformedTags.push(tag.value);
      });
      command.tags = transformedTags;

      return command;
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildSubmittableCommand: buildSubmittableCommand,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

