'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, accountService, orcaService) {
    $scope.securityGroup = {
      account: 'test',
      subnet: 'none'
    };

    $scope.subnetOptions = [
      {
        name: 'none',
        label: 'None (EC2 Classic)'
      },
      {
        name: 'internal',
        label: 'Internal'
      },
      {
        name: 'external',
        label: 'External'
      },
    ];

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
    });

    this.isValid = function () {
      var command = $scope.command;
      return command;
    };

    this.create = function () {
      console.warn(orcaService);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
