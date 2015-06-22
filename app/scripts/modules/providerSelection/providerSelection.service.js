'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.providerSelection.service', [
  require('../account/accountService.js'),
])
  .factory('providerSelectionService', function($modal, $q, accountService) {
    function selectProvider() {
      return accountService.listProviders().then(function(providers) {
        var provider;

        if (providers.length > 1) {
          provider = $modal.open({
            template: require('./providerSelection.html'),
            controller: 'ProviderSelectCtrl as ctrl',
            resolve: {
              providerOptions: function() { return providers; }
            }
          }).result;
        } else if (providers.length === 1) {
          provider = $q.when(providers[0]);
        } else {
          provider = $q.when('aws');
        }
        return provider;
      });
    }

    return {
      selectProvider: selectProvider,
    };

  });
