'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account.service', [
  require('../api/api.service'),
  require('../utils/lodash.js'),
  require('../cache/infrastructureCaches.js'),
  require('../config/settings.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .factory('accountService', function(settings, $log, _, API, $q, infrastructureCaches, cloudProviderRegistry) {

    let getAllAccountDetailsForProvider = _.memoize((providerName) => {
      return listAccounts(providerName)
        .then((accounts) => $q.all(accounts.map((account) => getAccountDetails(account.name))))
        .catch((e) => {
          $log.warn('Failed to load accounts for provider "' + providerName + '"; exception:', e);
          return [];
        });
    });

    let getPreferredZonesByAccount = _.memoize((providerName) => {
      let preferredZonesByAccount = {};
      return getAllAccountDetailsForProvider(providerName)
        .then((accounts) => {
          accounts.forEach((account) => {
            preferredZonesByAccount[account.name] = {};
            account.regions.forEach((region) => {
              let preferredZones = region.availabilityZones;
              //TODO(chrisberry)
              //Make this pluggable to remove the need for provider tests
              if (providerName === 'azure') {
                preferredZones = [region.name];
              }
              if (region.preferredZones) {
                preferredZones = _.intersection(region.preferredZones, region.availabilityZones);
              }
              preferredZonesByAccount[account.name][region.name] = preferredZones;
            });
          });
          return preferredZonesByAccount;
        });
    });

    function getAvailabilityZonesForAccountAndRegion(providerName, accountName, regionName) {
      return getPreferredZonesByAccount(providerName).then( function(result) {
        return result[accountName] ? result[accountName][regionName] : [];
      });
    }

    function listAccounts(provider) {
      if (provider) {
        return listAccounts().then(function(accounts) {
          return _.filter(accounts, { type: provider });
        });
      }
      return API
        .one('credentials')
        .useCache()
        .get();
    }

    let listProviders = (application) => {
      return listAccounts().then(function(accounts) {
        let allProviders = _.uniq(_.pluck(accounts, 'type'));
        let availableRegisteredProviders = _.intersection(allProviders, cloudProviderRegistry.listRegisteredProviders());
        if (application) {
          let appProviders = application.attributes.cloudProviders ?
            application.attributes.cloudProviders.split(',') :
            settings.defaultProviders ?
              settings.defaultProviders :
              availableRegisteredProviders;
          return _.intersection(availableRegisteredProviders, appProviders).sort();
        }
        return availableRegisteredProviders.sort();
      });
    };

    let getCredentialsKeyedByAccount = _.memoize((provider) => {
      var deferred = $q.defer();
      listAccounts(provider).then(function(accounts) {
        $q.all(accounts.reduce(function(acc, account) {
          acc[account.name] = API
            .one('credentials')
            .one(account.name)
            .useCache()
            .get();
          return acc;
        }, {})).then(function(result) {
          deferred.resolve(result);
        });
      });
      return deferred.promise;
    });

    let getUniqueAttributeForAllAccounts = (attribute) => {
      return _.memoize((provider) => {
        return getCredentialsKeyedByAccount(provider)
          .then(function(credentialsByAccount) {
            let attributes = _(credentialsByAccount)
              .pluck(attribute)
              .flatten()
              .compact()
              .map(reg => reg.name || reg)
              .uniq()
              .value();

            return attributes;
          });
       });
    };

    let getUniqueGceZonesForAllAccounts = _.memoize((provider) => {
      return getCredentialsKeyedByAccount(provider)
        .then(function(regionsByAccount) {
          return _(regionsByAccount)
            .pluck('regions')
            .flatten()
            .reduce((acc, obj) => {
              Object.keys(obj).forEach((key) => {
                if(acc[key]) {
                  acc[key] = _.uniq(acc[key].concat(obj[key]));
                } else {
                  acc[key] = obj[key];
                }
              });
              return acc;
            }, {});
        });
    });

    function getAccountDetails(accountName) {
      return API
        .one('credentials')
        .one(accountName)
        .useCache()
        .get();
    }

    function getRegionsForAccount(accountName) {
      return getAccountDetails(accountName).then(function(details) {
        return details ? details.regions : [];
      });
    }

    let challengeDestructiveActions = _.memoize((account) => {
      if (!account) {
        return $q.when(false);
      }
      let deferred = $q.defer();
      getAccountDetails(account).then(
        (details) => deferred.resolve(details ? details.challengeDestructiveActions : false),
        () => deferred.resolve(false));
      return deferred.promise;
    });

    return {
      challengeDestructiveActions: challengeDestructiveActions,
      listAccounts: listAccounts,
      listProviders: listProviders,
      getAccountDetails: getAccountDetails,
      getAllAccountDetailsForProvider: getAllAccountDetailsForProvider,
      getRegionsForAccount: getRegionsForAccount,
      getCredentialsKeyedByAccount: getCredentialsKeyedByAccount,
      getPreferredZonesByAccount: getPreferredZonesByAccount,
      getUniqueAttributeForAllAccounts: getUniqueAttributeForAllAccounts,
      getUniqueGceZonesForAllAccounts: getUniqueGceZonesForAllAccounts,
      getAvailabilityZonesForAccountAndRegion: getAvailabilityZonesForAccountAndRegion
    };
  });
