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
      },
      controller: function ($scope, accountService) {
        accountService.challengeDestructiveActions($scope.account).then((isProdAccount) => {
          $scope.accountType = isProdAccount ? 'prod' : $scope.account;
        });
      }
    };
  });



