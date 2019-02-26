'use strict';

const angular = require('angular');
import { AccountService } from 'core/account/AccountService';

module.exports = angular
  .module('spinnaker.core.account.collapsibleAccountTag.directive', [])
  .directive('collapsibleAccountTag', function() {
    return {
      restrict: 'E',
      templateUrl: require('./collapsibleAccountTag.directive.html'),
      scope: {},
      bindToController: {
        account: '@',
        state: '=',
      },
      controllerAs: 'vm',
      controller: [
        '$scope',
        function($scope) {
          this.getIcon = () => (this.state.expanded ? 'down' : 'right');

          let getAccountType = () => {
            AccountService.challengeDestructiveActions(this.account).then(challenge => {
              this.accountType = challenge ? 'prod' : $scope.account;
            });
          };

          $scope.$watch(() => this.account, getAccountType);
        },
      ],
    };
  });
