'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.providerSelection.service', [
  require('../../../account/account.service.js'),
  require('../../../config/settings.js'),
  require('../../../utils/lodash.js'),
])
  .factory('providerSelectionService', function($modal, $q, _, accountService, settings) {
    function selectProvider(application) {
      return accountService.listProviders().then(function(providers) {
        var provider;

        let availableProviders = application && application.attributes.cloudProviders ?
          _.intersection(providers, application.attributes.cloudProviders.split(',')) : providers;

        if (availableProviders.length > 1) {
          provider = $modal.open({
            templateUrl: require('./providerSelection.html'),
            controller: 'ProviderSelectCtrl as ctrl',
            resolve: {
              providerOptions: function() { return availableProviders; }
            }
          }).result;
        } else if (availableProviders.length === 1) {
          provider = $q.when(availableProviders[0]);
        } else {
          provider = $q.when(settings.defaultProvider || 'aws');
        }
        return provider;
      });
    }

    return {
      selectProvider: selectProvider,
    };

  }).name;
