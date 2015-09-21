'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.account.collapsibleAccountTag.directive', [
    require('./account.service.js'),
  ])
  .directive('collapsibleAccountTag', function () {
    return {
      restrict: 'E',
      templateUrl: require('./collapsibleAccountTag.directive.html'),
      scope: {
        account: '@',
        provider: '@',
        state: '=',
      },
      controller: function ($scope, accountService) {
        const isProdAccount = accountService.challengeDestructiveActions($scope.provider, $scope.account);
        $scope.accountType = isProdAccount ? 'prod' : $scope.account;

        $scope.getIcon = () => $scope.state.expanded ? 'down' : 'up';
      }
    };
  })
  .name;
