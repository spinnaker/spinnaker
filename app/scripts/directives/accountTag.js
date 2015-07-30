'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    template: '<span class="label label-default account-label account-label-{{getAccountType()}} {{pad}}">{{account}}</span>',
    scope: {
      account: '=',
      provider: '=?',
      pad: '@?'
    },
    controller: function($scope, settings ) {

      $scope.getAccountType = function() {
        if($scope.provider) {
          var prodAccounts = settings.providers[$scope.provider].challengeDestructiveActions || [];
          return prodAccounts.indexOf($scope.account) > -1 ? 'prod' : $scope.account;
        } else {
          return $scope.account;
        }
      };

    },
  };
};
