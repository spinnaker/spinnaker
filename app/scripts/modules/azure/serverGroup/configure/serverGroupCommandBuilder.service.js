'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('azureServerGroupCommandBuilder', function ($q, settings, azureImageReader) {

    function buildNewServerGroupCommand (application, defaults) {
      defaults = defaults || {};

      var imageLoader = azureImageReader.findImages({provider: 'azure',});

      var defaultCredentials = defaults.account || application.defaultCredentials || settings.providers.azure.defaults.account;
      var defaultRegion = defaults.region || application.defaultRegion || settings.providers.azure.defaults.region;

      return $q.all({
        images: imageLoader,
      }).then(function(backingData) {
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

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
    };
});

