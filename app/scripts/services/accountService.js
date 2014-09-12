'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('accountService', function(settings, Restangular, $q) {

    var detailsCache = {},
        credentialsCache = [];

    var credentialsEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.credentialsUrl);
    });

    function listAccounts() {
      var deferred = $q.defer();
      if (credentialsCache.length) {
        deferred.resolve(credentialsCache);
      } else {
        credentialsEndpoint.all('credentials').getList().then(function(list) {
          credentialsCache = list;
          deferred.resolve(list);
        });
      }
      return deferred.promise;
    }

    function getRegionsKeyedByAccount() {
      var deferred = $q.defer();
      listAccounts().then(function(accounts) {
        $q.all(accounts.reduce(function(acc, account) {
          acc[account] = credentialsEndpoint.all('credentials').one(account).get();
          return acc;
        }, {})).then(function(result) {
          deferred.resolve(result);
        });
      });
      return deferred.promise;
    }

    function getAccountDetails(accountName) {
      var deferred = $q.defer();
      if (detailsCache[accountName]) {
        deferred.resolve(detailsCache[accountName]);
      } else {
        credentialsEndpoint.one('credentials', accountName).get().then(function(details) {
          detailsCache[accountName] = details;
          deferred.resolve(details);
        });
      }
      return deferred.promise;
    }

    function getRegionsForAccount(accountName) {
      return getAccountDetails(accountName).then(function(details) {
        return details ? details.regions : null;
      });
    }

    function challengeDestructiveActions(account) {
      return account && settings.accounts[account] && Boolean(settings.accounts[account].challengeDestructiveActions);
    }

    return {
      challengeDestructiveActions: challengeDestructiveActions,
      listAccounts: listAccounts,
      getAccountDetails: getAccountDetails,
      getRegionsForAccount: getRegionsForAccount,
      getRegionsKeyedByAccount: getRegionsKeyedByAccount
    };
  });
