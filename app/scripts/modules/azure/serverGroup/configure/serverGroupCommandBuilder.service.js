'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
])
  .factory('azureServerGroupCommandBuilder', function ($q, azureImageReader) {

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

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
    };
  });

