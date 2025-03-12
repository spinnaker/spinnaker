'use strict';

import { module } from 'angular';

import { AccountService } from '../../account/AccountService';

import './userVerification.directive.less';

export const CORE_TASK_VERIFICATION_USERVERIFICATION_DIRECTIVE =
  'spinnaker.core.task.verification.userVerification.directive';
export const name = CORE_TASK_VERIFICATION_USERVERIFICATION_DIRECTIVE; // for backwards compatibility
module(CORE_TASK_VERIFICATION_USERVERIFICATION_DIRECTIVE, [])
  .directive('userVerification', function () {
    /**
     * The user verification directive takes at least two arguments
     */
    return {
      restrict: 'E',
      templateUrl: require('./userVerification.directive.html'),
      scope: {},
      bindToController: {
        verification: '=',
        account: '=',
        label: '=?',
        autofocus: '=?',
      },
      controllerAs: 'vm',
      controller: 'UserVerificationCtrl',
    };
  })
  .controller('UserVerificationCtrl', [
    '$scope',
    function ($scope) {
      this.$onInit = () => {
        this.label =
          this.label ||
          `Type the name of the account (<span class="verification-text">${this.account}</span>) to continue`;
        this.userVerification = '';
        this.required = false;
        this.verification.verified = true;
        $scope.$watch(() => this.account, initialize);
      };

      const initialize = () => {
        if (this.verification.toVerify) {
          this.required = true;
          this.verification.verified = false;
        }
        if (this.account) {
          this.verification.toVerify = this.account;
          AccountService.challengeDestructiveActions(this.account).then((challenge) => {
            this.required = challenge;
            this.verification.verified = !challenge;
          });
        }
      };

      this.verify = () => {
        this.verification.verified = this.userVerification.toUpperCase() === this.verification.toVerify.toUpperCase();
      };
    },
  ]);
