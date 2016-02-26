'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroupCommandBuilder.service', [
  require('../../image/image.reader.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('azureServerGroupCommandBuilder', function ($q, settings, azureImageReader, loadBalancerReader) {

    function buildNewServerGroupCommand (application, defaults) {
      defaults = defaults || {};

      var imageLoader = azureImageReader.findImages({provider: 'azure',});

      var defaultCredentials = defaults.account || application.defaultCredentials || settings.providers.azure.defaults.account;
      var defaultRegion = defaults.region || application.defaultRegion || settings.providers.azure.defaults.region;

      return $q.all({
        images: imageLoader,
        loadBalancers: loadBalancerReader.loadLoadBalancers(application.name),
      }).then(function(backingData) {
        return {
          application: application.name,
          credentials: defaultCredentials,
          region: defaultRegion,
          images: backingData.images,
          loadBalancers: [],
          strategy: '',
          capacity: {
            min: 1,
            max: 1,
            desired: 1
          },
          selectedProvider: 'azure',
          securityGroups: [],
          viewState: {
            instanceProfile: 'custom',
            allImageSelection: null,
            useAllImageSelection: false,
            useSimpleCapacity: true,
            usePreferredZones: true,
            mode: defaults.mode || 'create',
            disableStrategySelection: true,
            loadBalancersConfigured: false,
          },
        };
      });
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
    };
});

