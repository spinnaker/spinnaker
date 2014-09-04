'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateLoadBalancerCtrl', function($scope, $modalInstance, accountService, orcaService) {
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

    this.resize = function () {
      var capacity = { min: $scope.command.min, max: $scope.command.max, desired: $scope.command.desired };
      if (!$scope.command.advancedMode) {
        capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };
      }
      orcaService.resizeServerGroup(serverGroup, capacity)
        .then(function (response) {
          $modalInstance.close();
          console.warn('task:', response.ref);
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
