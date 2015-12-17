'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.providerSelection.service', [
  require('../../account/account.service.js'),
  require('../../config/settings.js'),
  require('../../utils/lodash.js'),
  require('../cloudProvider.registry.js'),
])
  .factory('providerSelectionService', function($uibModal, $q, _, accountService, settings, cloudProviderRegistry) {
    function selectProvider(application, feature) {
      return accountService.listProviders(application).then((providers) => {
        var provider;

        if (feature) {
          providers = providers.filter((provider) => cloudProviderRegistry.hasValue(provider, feature));
        }

        if (providers.length > 1) {
          provider = $uibModal.open({
            templateUrl: require('./providerSelection.html'),
            controller: 'ProviderSelectCtrl as ctrl',
            resolve: {
              providerOptions: function() { return providers; }
            }
          }).result;
        } else if (providers.length === 1) {
          provider = $q.when(providers[0]);
        } else {
          provider = $q.when(settings.defaultProvider || 'aws');
        }
        return provider;
      });
    }

    return {
      selectProvider: selectProvider,
    };

  });
