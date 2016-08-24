'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.titus.serverGroupCommandBuilder.service', [
  require('../../../core/cache/deckCacheFactory.js'),
  require('../../../core/account/account.service.js'),
  require('../../../core/naming/naming.service.js')
])
  .factory('titusServerGroupCommandBuilder', function (settings, $q,
                                                     accountService, namingService) {
    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || settings.providers.titus.defaults.account;
      var defaultRegion = defaults.region || settings.providers.titus.defaults.region;
      var defaultZone = defaults.zone || settings.providers.titus.defaults.zone;

      var command = {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
        zone: defaultZone,
        network: 'default',
        inService: true,
        strategy: '',
        capacity: {
          min: 1,
          max: 1,
          desired: 1
        },
        env: {},
        labels: {},
        cloudProvider: 'titus',
        selectedProvider: 'titus',
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
        env: serverGroup.env,
        labels: serverGroup.labels,
        entryPoint: serverGroup.entryPoint,
        iamProfile: serverGroup.iamProfile,
        securityGroups: serverGroup.securityGroups,
        inService: serverGroup.disabled ? false : true,
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
          allocateIpAddress: serverGroup.resources.allocateIpAddress,
        },
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired
        },
        cloudProvider: 'titus',
        selectedProvider: 'titus',
        viewState: {
          useSimpleCapacity: true,
          mode: mode,
        },
      };

      if (mode !== 'editPipeline') {
        command.imageId = serverGroup.image.dockerImageName + ':' + serverGroup.image.dockerImageVersion;
      }

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

