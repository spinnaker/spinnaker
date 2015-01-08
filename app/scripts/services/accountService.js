'use strict';


angular.module('deckApp')
  .factory('accountService', function(settings, _, Restangular, $q, scheduledCache, infrastructureCaches) {

    var preferredZonesByAccount = {
      prod: {
        'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
        'us-west-1': ['us-west-1a', 'us-west-1c'],
        'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
        'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
        'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
        'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
        'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
        'sa-east-1': ['sa-east-1a', 'sa-east-1b']
      },
      test: {
        'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
        'us-west-1': ['us-west-1a', 'us-west-1c'],
        'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
        'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
        'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
        'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
        'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
        'sa-east-1': ['sa-east-1a', 'sa-east-1b']
      },
      mceprod: {
        'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
        'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
        'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
        'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
        'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
        'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
        'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
        'sa-east-1': ['sa-east-1a', 'sa-east-1b']
      },
      mcetest: {
        'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d'],
        'us-west-1': ['us-west-1b', 'us-west-1c'],
        'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
        'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
        'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
        'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
        'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
        'sa-east-1': ['sa-east-1a', 'sa-east-1b']
      },
      default: {
        'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
        'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
        'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
        'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
        'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
        'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
        'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
        'sa-east-1': ['sa-east-1a', 'sa-east-1b']
      }
    };

    function getPreferredZonesByAccount() {
      return $q.when(preferredZonesByAccount);
    }

    function listAccounts() {
      return Restangular
        .all('credentials')
        .withHttpConfig({cache: infrastructureCaches.credentials})
        .getList();
    }

    function listProviders() {
      return listAccounts().then(function(accounts) {
        return _.pluck(accounts, 'type');
      });
    }

    function getRegionsKeyedByAccount() {
      var deferred = $q.defer();
      listAccounts().then(function(accounts) {
        $q.all(accounts.reduce(function(acc, account) {
          acc[account.name] = Restangular
            .all('credentials')
            .one(account.name)
            .withHttpConfig({cache: infrastructureCaches.credentials})
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
        .withHttpConfig({cache: infrastructureCaches.credentials})
        .get();
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
      listProviders: listProviders,
      getAccountDetails: getAccountDetails,
      getRegionsForAccount: getRegionsForAccount,
      getRegionsKeyedByAccount: getRegionsKeyedByAccount,
      getPreferredZonesByAccount: getPreferredZonesByAccount
    };
  });
