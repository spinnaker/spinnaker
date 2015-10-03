'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titan.serverGroupCommandBuilder.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../caches/deckCacheFactory.js'),
  require('../../../account/account.service.js'),
  require('../../../naming/naming.service.js'),
  require('../../../utils/lodash.js'),
])
  .factory('titanServerGroupCommandBuilder', function (settings, Restangular, $exceptionHandler, $q,
                                                     accountService, instanceTypeService, namingService, _) {

    // Two assumptions here:
    //   1) All GCE machine types are represented in the tree of choices.
    //   2) Each machine type appears in exactly one category.
    function determineInstanceCategoryFromInstanceType(command) {
      return instanceTypeService.getCategories('titan').then(function(categories) {
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
      return accountService.listAccounts('titan').then(function(titanAccounts) {
        var titanAccountNames = _.pluck(titanAccounts, 'name');
        var firstGCEAccount = null;

        if (application.accounts.length) {
          firstGCEAccount = _.find(application.accounts, function (applicationAccount) {
            return titanAccountNames.indexOf(applicationAccount) !== -1;
          });
        }

        var defaultCredentialsAreValid = defaultCredentials && titanAccountNames.indexOf(defaultCredentials) !== -1;

        command.credentials =
          defaultCredentialsAreValid ? defaultCredentials : (firstGCEAccount ? firstGCEAccount : 'my-account-name');
      });
    }

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || settings.providers.titan.defaults.account;
      var defaultRegion = defaults.region || settings.providers.titan.defaults.region;
      var defaultZone = defaults.zone || settings.providers.titan.defaults.zone;

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        zone: defaultZone,
        network: 'default',
        strategy: '',
        resources: {
          cpu: 1,
          memory: 1000,
          disk: 1000,
          ports: 7001,
        },
        capacity: {
          min: 0,
          max: 0,
          desired: 1
        },
        tags: [],
        cloudProvider: 'titan',
        providerType: 'titan',
        selectedProvider: 'titan',
        viewState: {
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
        account: serverGroup.account,
        credentials: serverGroup.account,
        region: serverGroup.region,
        resources: {
          cpu: serverGroup.resources.cpu,
          memory: serverGroup.resources.memory,
          disk: serverGroup.resources.disk,
          ports: serverGroup.resources.ports,
        },
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired
        },
        cloudProvider: 'titan',
        providerType: 'titan',
        selectedProvider: 'titan',
        viewState: {
          useSimpleCapacity: true,
          mode: mode,
        },
      };

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      var zone = pipelineCluster.zone;
      var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('titan', pipelineCluster.instanceType);
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

