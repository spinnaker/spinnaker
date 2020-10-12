'use strict';

import { module } from 'angular';
import { AccountService } from './AccountService';

export const CORE_ACCOUNT_COLLAPSIBLEACCOUNTTAG_DIRECTIVE = 'spinnaker.core.account.collapsibleAccountTag.directive';
export const name = CORE_ACCOUNT_COLLAPSIBLEACCOUNTTAG_DIRECTIVE; // for backwards compatibility
module(CORE_ACCOUNT_COLLAPSIBLEACCOUNTTAG_DIRECTIVE, []).directive('collapsibleAccountTag', function () {
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
      function ($scope) {
        this.getIcon = () => (this.state.expanded ? 'down' : 'right');

        const getAccountType = () => {
          AccountService.challengeDestructiveActions(this.account).then((challenge) => {
            this.accountType = challenge ? 'prod' : $scope.account;
          });
        };

        $scope.$watch(() => this.account, getAccountType);
      },
    ],
  };
});
