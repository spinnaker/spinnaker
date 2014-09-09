'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateLoadBalancerCtrl', function($scope, $modalInstance, accountService) {
    $scope.loadBalancer = {
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


    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
