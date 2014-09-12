'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('accountService', function(settings, Restangular, $q) {

    var credentialsEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setDefaultHttpFields({ cache: true });
      RestangularConfigurer.setBaseUrl(settings.credentialsUrl);
    });

    function listAccounts() {
      return credentialsEndpoint.all('credentials').getList();
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
      credentialsEndpoint.one('credentials', accountName).get();
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
