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
        loading: '=',
        onChange: '&',
        labelColumns: '@',
        labelAlign: '@'
      },
      link: function(scope) {
        function groupAccounts(accounts) {
          if (accounts) {
            scope.primaryAccounts = accounts.sort();
          }
          if (accounts && accounts.length) {
            scope.primaryAccounts = accounts.filter(function(account) {
                return settings.primaryAccounts.indexOf(account) !== -1;
            }).sort();
            scope.secondaryAccounts = _.xor(accounts, scope.primaryAccounts).sort();
          }
        }
        scope.$watch('accounts', groupAccounts);
      }
    };
  }
);
