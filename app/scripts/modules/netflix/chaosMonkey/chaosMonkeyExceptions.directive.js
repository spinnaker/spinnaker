'use strict';

const angular = require('angular');

require('./chaosMonkeyExceptions.directive.less');

module.exports = angular
  .module('spinnaker.netflix.chaosMonkey.exceptions.directive', [
    require('../../core/account/account.service.js'),
  ])
  .directive('chaosMonkeyExceptions', function () {
    return {
      restrict: 'E',
      templateUrl: require('./chaosMonkeyExceptions.directive.html'),
      scope: {},
      bindToController: {
        config: '=',
      },
      controller: 'ChaosMonkeyExceptionsCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ChaosMonkeyExceptionsCtrl', function($q, accountService) {
    this.addException = () => {
      this.config.exceptions = this.config.exceptions || [];
      this.config.exceptions.push({});
    };

    this.removeException = (index) => {
      this.config.exceptions.splice(index, 1);
    };

    accountService.listAccounts('aws').then((accounts) => {
      $q.all(accounts.map((account) => accountService.getAccountDetails(account.name)))
        .then((details) => {
          this.accounts = details;
          this.regionsByAccount = {};
          details.forEach((account) => {
            this.regionsByAccount[account.name] = ['*'].concat(account.regions.map((region) => region.name));
          });
        });
    });

  })
  .name;
