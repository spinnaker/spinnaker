'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.account.accountTag.directive', [
    require('./account.service.js'),
  ])
  .directive('accountTag', function () {
    return {
      restrict: 'E',
      template: '<span class="label label-default account-label account-label-{{accountType}} {{pad}}">{{account}}</span>',
      scope: {
        account: '=',
        pad: '@?'
      },
      controller: function($scope, accountService) {
        function getAccountType() {
          accountService.challengeDestructiveActions($scope.account).then((challenge) => {
            $scope.accountType = challenge ? 'prod' : $scope.account;
          });
        }
        $scope.$watch('account', getAccountType);
      },
    };
});
