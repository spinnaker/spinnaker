'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.serverGroupCommandBuilder.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../core/cache/deckCacheFactory.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/instance/instanceTypeService.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('gceServerGroupCommandBuilder', function (settings, Restangular, $q,
                                                     accountService, instanceTypeService, namingService, _) {

    // Two assumptions here:
    //   1) All GCE machine types are represented in the tree of choices.
    //   2) Each machine type appears in exactly one category.
    function determineInstanceCategoryFromInstanceType(command) {
      return instanceTypeService.getCategories('gce').then(function(categories) {
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

    function extractNetworkName(serverGroup) {
      if (_.has(serverGroup, 'launchConfig.instanceTemplate.properties.networkInterfaces')) {
        var networkInterfaces = serverGroup.launchConfig.instanceTemplate.properties.networkInterfaces;
        if (networkInterfaces.length === 1) {
          var networkUrl = networkInterfaces[0].network;
          return _.last(networkUrl.split('/'));
        }
      }
      return null;
    }

    function populateCustomMetadata(metadataItems, command) {
      if (metadataItems) {
        if (angular.isArray(metadataItems)) {
          metadataItems.forEach(function (metadataItem) {
            // Don't show 'load-balancer-names' key/value pair in the wizard.
            if (metadataItem.key !== 'load-balancer-names') {
              // The 'key' and 'value' attributes are used to enable the Add/Remove behavior in the wizard.
              command.instanceMetadata.push(metadataItem);
            }
          });
        } else {
          for (var property in metadataItems) {
            // Don't show 'load-balancer-names' key/value pair in the wizard.
            if (property !== 'load-balancer-names') {
              // The 'key' and 'value' attributes are used to enable the Add/Remove behavior in the wizard.
              command.instanceMetadata.push({
                key: property,
                value: metadataItems[property],
              });
            }
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
      var defaultZone = defaults.zone || settings.providers.gce.defaults.zone;

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        zone: defaultZone,
        network: 'default',
        strategy: '',
        capacity: {
          min: 0,
          max: 0,
          desired: 1
        },
        instanceMetadata: [],
        tags: [],
        cloudProvider: 'gce',
        providerType: 'gce',
        selectedProvider: 'gce',
        availabilityZones: [],
        viewState: {
          instanceProfile: 'custom',
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: true,
          usePreferredZones: true,
          listImplicitSecurityGroups: false,
          mode: defaults.mode || 'create',
        }
      };

      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Google'];
      }

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
        loadBalancers: serverGroup.asg.loadBalancerNames,
        securityGroups: serverGroup.securityGroups,
        region: serverGroup.region,
        capacity: {
          min: serverGroup.asg.minSize,
          max: serverGroup.asg.maxSize,
          desired: serverGroup.asg.desiredCapacity
        },
        zone: serverGroup.zones[0],
        network: extractNetworkName(serverGroup),
        instanceMetadata: [],
        tags: [],
        availabilityZones: [],
        cloudProvider: 'gce',
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
          listImplicitSecurityGroups: false,
          mode: mode,
        },
      };

      if (application && application.attributes && application.attributes.platformHealthOnly) {
        command.interestingHealthProviderNames = ['Google'];
      }

      if (serverGroup.launchConfig) {
        angular.extend(command, {
          instanceType: serverGroup.launchConfig.instanceType,
        });
        command.viewState.imageId = serverGroup.launchConfig.imageId;
        return determineInstanceCategoryFromInstanceType(command).then(function() {
          populateCustomMetadata(serverGroup.launchConfig.instanceTemplate.properties.metadata.items, command);
          populateTags(serverGroup.launchConfig.instanceTemplate.properties.tags, command);
          return command;
        });
      }

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      var zone = pipelineCluster.zone;
      var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('gce', pipelineCluster.instanceType);
      var commandOptions = { account: pipelineCluster.account, region: region, zone: zone };
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

        var extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);

        var instanceMetadata = extendedCommand.instanceMetadata;
        extendedCommand.instanceMetadata = [];
        populateCustomMetadata(instanceMetadata, extendedCommand);

        var instanceTemplateTags = {items: extendedCommand.tags};
        extendedCommand.tags = [];
        populateTags(instanceTemplateTags, extendedCommand);

        return extendedCommand;
      });

    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
})
.name;

