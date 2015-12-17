'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titan.serverGroupCommandBuilder.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../../../core/cache/deckCacheFactory.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/utils/dataConverter.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('titanServerGroupCommandBuilder', function (settings, Restangular, $q,
                                                     accountService, namingService, _, dataConverterService) {
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
        capacity: {
          min: 0,
          max: 0,
          desired: 1
        },
        tags: [],
        cloudProvider: 'titan',
        selectedProvider: 'titan',
        viewState: {
          useSimpleCapacity: true,
          usePreferredZones: true,
          mode: defaults.mode || 'create',
        }
      };

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
        env: dataConverterService.keyValueToEqualList(serverGroup.env),
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
          asgName: serverGroup.name,
        },
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
      var commandOptions = { account: pipelineCluster.account, region: pipelineCluster.region };
      var asyncLoader = $q.all({command: buildNewServerGroupCommand(application, commandOptions)});

      return asyncLoader.then(function(asyncData) {
        var command = asyncData.command;

        var viewState = {
          disableImageSelection: true,
          useSimpleCapacity: true,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
        };

        var viewOverrides = {
          region: pipelineCluster.region,
          credentials: pipelineCluster.account,
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';
        var extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);
        extendedCommand.env = dataConverterService.keyValueToEqualList(extendedCommand.env);

        return extendedCommand;
      });

    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
});

