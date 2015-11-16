'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../utils/lodash.js'),
  require('../cache/infrastructureCaches.js'),
  require('../config/settings.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .factory('accountService', function(settings, _, Restangular, $q, infrastructureCaches, cloudProviderRegistry) {

    function getPreferredZonesByAccount(providerName='aws') {
      return $q.when(settings.providers[providerName].preferredZonesByAccount);
    }

    function getAvailabilityZonesForAccountAndRegion(providerName, accountName, regionName) {

      return getPreferredZonesByAccount(providerName).then( function(defaults) {
        if (defaults[accountName] && defaults[accountName][regionName]) {
          return {preferredZones: defaults[accountName][regionName]};
        }
        if (!defaults[accountName] && defaults.default && defaults.default[regionName]) {
          return {preferredZones: defaults.default[regionName]};
        }
        return {preferredZones: []};
      })
      .then(function(zonesCollection) {
        return getRegionsForAccount(accountName).then(function(regions){
          zonesCollection.actualZones = _.find(regions, {name: regionName}).availabilityZones;
          return zonesCollection;
        });
      })
      .then(function(zonesCollection) {
        return _.intersection(zonesCollection.preferredZones, zonesCollection.actualZones);
      })
      .catch(function() {
         return settings.providers[providerName].preferredZonesByAccount.default[regionName];
      });
    }

    function listAccounts(provider) {
      if (provider) {
        return listAccounts().then(function(accounts) {
          return _.filter(accounts, { type: provider });
        });
      }
      return Restangular
        .all('credentials')
        .withHttpConfig({cache: true})
        .getList();
    }

    function listProviders(application) {
      return listAccounts().then(function(accounts) {
        let allProviders = _.uniq(_.pluck(accounts, 'type'));
        let availableRegisteredProviders = _.intersection(allProviders, cloudProviderRegistry.listRegisteredProviders());
        if (application) {
          let appProviders = application.attributes.cloudProviders ?
            application.attributes.cloudProviders.split(',') :
            settings.defaultProviders ?
              settings.defaultProviders :
              availableRegisteredProviders;
          return _.intersection(availableRegisteredProviders, appProviders);
        }
        return availableRegisteredProviders;
      });
    }

    function getRegionsKeyedByAccount(provider) {
      var deferred = $q.defer();
      listAccounts(provider).then(function(accounts) {
        $q.all(accounts.reduce(function(acc, account) {
          acc[account.name] = Restangular
            .all('credentials')
            .one(account.name)
            .withHttpConfig({cache: true})
            .get();
          return acc;
        }, {})).then(function(result) {
          deferred.resolve(result);
        });
      });
      return deferred.promise;
    }

    function getAccountDetails(accountName) {
      return Restangular.one('credentials', accountName)
        .withHttpConfig({cache: true})
        .get();
    }

    function getRegionsForAccount(accountName) {
      return getAccountDetails(accountName).then(function(details) {
        return details ? details.regions : [];
      });
    }

    function challengeDestructiveActions(provider, account) {
      return account &&
        settings.providers[provider] &&
        settings.providers[provider].challengeDestructiveActions &&
        settings.providers[provider].challengeDestructiveActions.indexOf(account) > -1;
    }

    return {
      challengeDestructiveActions: challengeDestructiveActions,
      listAccounts: listAccounts,
      listProviders: listProviders,
      getAccountDetails: getAccountDetails,
      getRegionsForAccount: getRegionsForAccount,
      getRegionsKeyedByAccount: getRegionsKeyedByAccount,
      getPreferredZonesByAccount: getPreferredZonesByAccount,
      getAvailabilityZonesForAccountAndRegion: getAvailabilityZonesForAccountAndRegion
    };
  })
  .name;
