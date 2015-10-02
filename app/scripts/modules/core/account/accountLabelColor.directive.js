'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.account.accountLabelColor.directive', [
    require('./account.service.js'),
  ])
  .directive('accountLabelColor', function () {
    return {
      restrict: 'E',
      template: '<span class="account-tag account-tag-{{accountType}}">{{account}}</span>',
      scope: {
        account: '@',
        provider: '@'
      },
      controller: function ($scope, accountService) {
        const isProdAccount = accountService.challengeDestructiveActions($scope.provider, $scope.account);
        $scope.accountType = isProdAccount ? 'prod' : $scope.account;
      }
    };
  })
  .name;



