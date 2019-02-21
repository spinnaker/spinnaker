'use strict';

const angular = require('angular');

import { AccountService } from 'core/account/AccountService';

import './userVerification.directive.less';

module.exports = angular
  .module('spinnaker.core.task.verification.userVerification.directive', [])
  .directive('userVerification', function() {
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
    function($scope) {
      this.$onInit = () => {
        this.label =
          this.label ||
          `Type the name of the account (<span class="verification-text">${this.account}</span>) to continue`;
        this.userVerification = '';
        this.required = false;
        this.verification.verified = true;
        $scope.$watch(() => this.account, initialize);
      };

      let initialize = () => {
        if (this.verification.toVerify) {
          this.required = true;
          this.verification.verified = false;
        }
        if (this.account) {
          this.verification.toVerify = this.account;
          AccountService.challengeDestructiveActions(this.account).then(challenge => {
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
