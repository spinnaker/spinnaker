'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroupCommandBuilder.service', [
])
  .factory('azureServerGroupCommandBuilder', function (settings) {

    function buildNewServerGroupCommand (application, defaults) {
      defaults = defaults || {};

      var defaultCredentials = defaults.account || application.defaultCredentials || settings.providers.azure.defaults.account;
      var defaultRegion = defaults.region || application.defaultRegion || settings.providers.azure.defaults.region;

      return {
        application: application.name,
        credentials: defaultCredentials,
        region: defaultRegion,
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
        },
      };
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
    };
});

