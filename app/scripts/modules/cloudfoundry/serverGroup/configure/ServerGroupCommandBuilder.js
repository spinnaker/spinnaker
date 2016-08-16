'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.serverGroupCommandBuilder.service', [
  require('../../../core/cache/deckCacheFactory.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/instance/instanceTypeService.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('cfServerGroupCommandBuilder', function (settings, $q,
                                                     accountService, instanceTypeService, namingService, _) {

    function populateTags(instanceTemplateTags, command) {
      if (instanceTemplateTags && instanceTemplateTags.items) {
        _.map(instanceTemplateTags.items, function (tag) {
          command.tags.push({value: tag});
        });
      }
    }

    function attemptToSetValidCredentials(application, defaultCredentials, command) {
      return accountService.listAccounts('cf').then(function (cfAccounts) {
        var cfAccountNames = _.pluck(cfAccounts, 'name');
        var firstcfAccount = null;

        if (application.accounts.length) {
          firstcfAccount = _.find(application.accounts, function (applicationAccount) {
            return cfAccountNames.indexOf(applicationAccount) !== -1;
          });
        }

        var defaultCredentialsAreValid = defaultCredentials && cfAccountNames.indexOf(defaultCredentials) !== -1;

        command.credentials =
            defaultCredentialsAreValid ? defaultCredentials : (firstcfAccount ? firstcfAccount : 'my-account-name');
      });
    }

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || settings.providers.cf.defaults.account;
      var defaultRegion = defaults.region || settings.providers.cf.defaults.region;

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        network: 'default',
        strategy: '',
        capacity: {
          min: 1,
          max: 4,
          desired: 1
        },
        tags: [],
        cloudProvider: 'cf',
        providerType: 'cf',
        selectedProvider: 'cf',
        availabilityZones: [],
        loadBalancers: '',
        services: [],
        envs: [],
        repository: '',
        artifact: '',
        username: '',
        password: '',
        buildpackUrl: '',
        memory: 1024,
        disk: 1024,
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
        loadBalancers: serverGroup.loadBalancers,
        services: serverGroup.services,
        envs: serverGroup.envs,
        securityGroups: serverGroup.securityGroups,
        region: serverGroup.region,
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired
        },
        tags: serverGroup.tags,
        availabilityZones: serverGroup.availabilityZones,
        cloudProvider: 'cf',
        providerType: 'cf',
        selectedProvider: 'cf',
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          serverGroupName: serverGroup.name,
          asgName: serverGroup.name
        },
        repository: serverGroup.repository,
        artifact: serverGroup.artifact,
        username: serverGroup.username,
        password: serverGroup.password,
        buildpackUrl: serverGroup.buildpackUrl,
        memory: serverGroup.memory,
        disk: serverGroup.disk,
        viewState: {
          allImageSelection: null,
          useAllImageSelection: false,
          useSimpleCapacity: true,
          usePreferredZones: false,
          listImplicitSecurityGroups: false,
          mode: mode,
        },
      };

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = Object.keys(pipelineCluster.availabilityZones)[0];
      var instanceTypeCategoryLoader = instanceTypeService.getCategoryForInstanceType('cf', pipelineCluster.instanceType);
      var commandOptions = {account: pipelineCluster.account, region: region};
      var asyncLoader = $q.all({
        command: buildNewServerGroupCommand(application, commandOptions),
        instanceProfile: instanceTypeCategoryLoader
      });

      return asyncLoader.then(function (asyncData) {
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

        var instanceTemplateTags = {items: extendedCommand.tags};
        extendedCommand.tags = [];
        populateTags(instanceTemplateTags, extendedCommand);

        return extendedCommand;
      });

    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

