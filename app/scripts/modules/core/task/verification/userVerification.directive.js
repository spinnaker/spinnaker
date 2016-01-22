'use strict';

let angular = require('angular');

require('./userVerification.directive.less');

module.exports = angular
  .module('spinnaker.core.task.verification.userVerification.directive', [
    require('../../account/account.service.js'),
  ])
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
        label: '=?'
      },
      controllerAs: 'vm',
      controller: 'UserVerificationCtrl',
    };
  })
  .controller('UserVerificationCtrl', function ($scope, accountService) {

    this.label = this.label || `Type the name of the account (<span class="verification-text">${this.account}</span>) below to continue.`;
    this.userVerification = '';
    this.required = false;
    this.verification.verified = true;

    let initialize = () => {
      if (this.verification.toVerify) {
        this.required = true;
        this.verification.verified = false;
      }
      if (this.account) {
        this.verification.toVerify = this.account;
        accountService.challengeDestructiveActions(this.account).then((challenge) => {
          this.required = challenge;
          this.verification.verified = !challenge;
        });
      }
    };

    this.verify = () => {
      this.verification.verified = this.userVerification.toUpperCase() === this.verification.toVerify.toUpperCase();
    };

    $scope.$watch(() => this.account, initialize);
  });
