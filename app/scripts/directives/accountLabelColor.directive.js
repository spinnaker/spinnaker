'use strict';

angular
  .module('spinnaker.accountLabelColor.directive', [
    'spinnaker.settings'
  ])
  .directive('accountLabelColor', function () {
    return {
      restrict: 'E',
      template: '<span class="account-tag account-tag-{{getAccountType()}}">{{account}}</span>',
      scope: {
        account: '@',
        provider: '@'
      },
      controller: function ($scope, settings) {
        var prodAccounts = settings.providers[$scope.provider].challengeDestructiveActions || [];
        $scope.getAccountType = function() {
          return prodAccounts.indexOf($scope.account) > -1 ? 'prod' : $scope.account;
        };
      }
    };
  });



