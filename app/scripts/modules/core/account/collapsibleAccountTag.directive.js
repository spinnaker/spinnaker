'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.account.collapsibleAccountTag.directive', [
    require('./account.service.js'),
  ])
  .directive('collapsibleAccountTag', function () {
    return {
      restrict: 'E',
      templateUrl: require('./collapsibleAccountTag.directive.html'),
      scope: {},
      bindToController: {
        account: '@',
        state: '=',
      },
      controllerAs: 'vm',
      controller: function ($scope, accountService) {
        this.getIcon = () => this.state.expanded ? 'down' : 'up';

        let getAccountType = () => {
          accountService.challengeDestructiveActions(this.account).then((challenge) => {
            this.accountType = challenge ? 'prod' : $scope.account;
          });
        };

        $scope.$watch(() => this.account, getAccountType);

      }
    };
  });
