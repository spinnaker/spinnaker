'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
  require('../../../core/naming/naming.service.js'),
])
  .factory('azureServerGroupCommandBuilder', function ($q, azureImageReader, namingService) {

    function buildNewServerGroupCommand(application, defaults) {
      defaults = defaults || {};

      var imageLoader = azureImageReader.findImages({ provider: 'azure', });

      var defaultCredentials = defaults.account || application.defaultCredentials;
      var defaultRegion = defaults.region || application.defaultRegion;

      return $q.all({
        images: imageLoader,
      }).then(function (backingData) {
        return {
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
          images: backingData.images,
          loadBalancers: [],
          securityGroups: [],
          strategy: '',
          sku: {
            capacity: 1,
          },
          selectedProvider: 'azure',
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: true,
            usePreferredZones: true,
            mode: defaults.mode || 'create',
            disableStrategySelection: true,
            loadBalancersConfigured: false,
            securityGroupsConfigured: false,
          },
        };
      });
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
        securityGroups: serverGroup.securityGroups,
        loadBalancerName: serverGroup.appGatewayName,
        securityGroup: {
          id: serverGroup.securityGroupName,
        },
        region: serverGroup.region,
        sku: serverGroup.sku,
        capacity: {
          min: serverGroup.capacity.min,
          max: serverGroup.capacity.max,
          desired: serverGroup.capacity.desired
        },
        tags: [],
        selectedProvider: 'azure',
        source: {
          account: serverGroup.account,
          region: serverGroup.region,
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
          disableStrategySelection: true,
        },
      };

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster) {

      var pipelineCluster = _.cloneDeep(originalCluster);
      var region = pipelineCluster.region;
      var commandOptions = {account: pipelineCluster.account, region: region};
      var asyncLoader = $q.all({
        command: buildNewServerGroupCommand(application, commandOptions)
      });

      return asyncLoader.then(function (asyncData) {
        var command = asyncData.command;

        var viewState = {
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

