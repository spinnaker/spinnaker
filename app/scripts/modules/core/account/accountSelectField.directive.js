'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account.accountSelectField.directive', [
  require('./account.service.js'),
])
  .directive('accountSelectField', function($q, _, accountService) {
  return {
    restrict: 'E',
    templateUrl: require('./accountSelectField.directive.html'),
    scope: {
      accounts: '=',
      component: '=',
      field: '@',
      provider: '=',
      loading: '=',
      onChange: '&',
      labelColumns: '@',
      labelAlign: '@',
      readOnly: '=',
      multiselect: '='
    },
    link: function(scope) {
      scope.mergedAccounts = [];

      function groupAccounts(accounts) {
        let getAccountDetails = scope.provider ?
          accountService.getAllAccountDetailsForProvider(scope.provider) :
          $q.when([]);

        getAccountDetails.then((details) => {
          let accountNames = accounts;

          if (accounts && accounts[0] && accounts[0].name) {
            accountNames = _.pluck(accounts, 'name');
          }
          scope.mergedAccounts = accountNames;
          if (accountNames) {
            scope.primaryAccounts = accountNames.sort();
          }
          if (accountNames && accountNames.length && details.length) {
            scope.primaryAccounts = accountNames.filter(function(account) {
              return details.some((detail) => detail.name === account && detail.primaryAccount);
            }).sort();
            scope.secondaryAccounts = _.xor(accountNames, scope.primaryAccounts).sort();
            scope.mergedAccounts = _.flatten([scope.primaryAccounts, scope.secondaryAccounts]);
          }
        });
      }

      scope.groupBy = function(account) {
        if(scope.secondaryAccounts && scope.secondaryAccounts.indexOf(account) > -1) {
          return '---------------';
        }

        if(scope.primaryAccounts.indexOf(account) > -1) {
          return undefined;
        }
      };

      scope.$watch('accounts', groupAccounts);
    }
  };
}).name;
