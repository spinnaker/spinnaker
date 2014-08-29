'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ResizeServerGroupCtrl', function($scope, $modalInstance, accountService, orcaService, application, serverGroup) {
    $scope.serverGroup = serverGroup;
    $scope.currentSize = {
      min: serverGroup.asg.minSize,
      max: serverGroup.asg.maxSize,
      desired: serverGroup.asg.desiredCapacity,
      newSize: null
    };

    $scope.verification = {
      required: accountService.challengeDestructiveActions(serverGroup.account)
    };

    $scope.command = angular.copy($scope.currentSize);
    $scope.command.advancedMode = serverGroup.asg.minSize !== serverGroup.asg.maxSize;

    this.isValid = function () {
      var command = $scope.command;
      if ($scope.verification.required && $scope.verification.verifyAccount !== serverGroup.account) {
        return false;
      }
      return command.advancedMode ?
        command.min <= command.max && command.desired >= command.min && command.desired <= command.max :
        command.newSize !== null;
    };

    this.resize = function () {
      var capacity = { min: $scope.command.min, max: $scope.command.max, desired: $scope.command.desired };
      if (!$scope.command.advancedMode) {
        capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };
      }
      orcaService.resizeServerGroup(serverGroup, capacity, application.name)
        .then(function (response) {
          $modalInstance.close();
          console.warn('task:', response.ref);
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
