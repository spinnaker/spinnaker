'use strict';

const angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.core.account.collapsibleAccountTag.directive', [ACCOUNT_SERVICE])
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
        this.getIcon = () => this.state.expanded ? 'down' : 'right';

        let getAccountType = () => {
          accountService.challengeDestructiveActions(this.account).then((challenge) => {
            this.accountType = challenge ? 'prod' : $scope.account;
          });
        };

        $scope.$watch(() => this.account, getAccountType);

      }
    };
  });
