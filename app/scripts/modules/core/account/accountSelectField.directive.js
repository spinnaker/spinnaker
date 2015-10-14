'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.account.accountSelectField.directive', [
  require('../config/settings.js')
])
  .directive('accountSelectField', function(settings, _) {
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
      function groupAccounts(accounts) {
        if (accounts && accounts[0] && accounts[0].name) {
          accounts = _.pluck(accounts, 'name');
        }
        if (accounts) {
          scope.primaryAccounts = accounts.sort();
        }
        if (accounts && accounts.length && settings.providers[scope.provider] && settings.providers[scope.provider].primaryAccounts) {
          scope.primaryAccounts = accounts.filter(function(account) {
              return settings.providers[scope.provider].primaryAccounts.indexOf(account) !== -1;
          }).sort();
          scope.secondaryAccounts = _.xor(accounts, scope.primaryAccounts).sort();
          scope.mergedAccounts = _.flatten([scope.primaryAccounts, scope.secondaryAccounts]);
        }

      }

      scope.groupBy = function(account) {
        if(scope.secondaryAccounts.indexOf(account) > -1) {
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
