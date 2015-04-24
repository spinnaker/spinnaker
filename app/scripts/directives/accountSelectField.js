'use strict';


angular.module('deckApp')
  .directive('accountSelectField', function (settings) {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/accountSelectField.html',
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
          }
        }
        scope.$watch('accounts', groupAccounts);
      }
    };
  }
);
