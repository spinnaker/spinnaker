'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupDetailsCtrl', function ($scope, application, serverGroup, orcaService, $modal, confirmationModalService) {

    function extractServerGroup(clusters) {
      clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (toCheck) {
          if (toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region) {
            $scope.serverGroup = toCheck;
            $scope.cluster = cluster;
            $scope.account = serverGroup.accountId;
            if (toCheck.launchConfig) {
              delete toCheck.launchConfig.userData;
            }
            return true;
          }
        });
      });
    }

    extractServerGroup(application.clusters);

    $scope.destroyServerGroup = function () {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account
      }).then(function () {
        orcaService.destroyServerGroup(serverGroup)
          .then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    // TODO: move to service
    $scope.disableServerGroup = function () {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        destructive: true,
        account: serverGroup.account
      }).then(function () {
        orcaService.disableServerGroup(serverGroup).then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    $scope.resizeServerGroup = function () {
      var serverGroup = $scope.serverGroup;
      $modal.open({
        templateUrl: 'views/application/modal/resizeServerGroup.html',
        controller: function ($scope, $modalInstance, pond, accountService) {

          $scope.serverGroup = serverGroup;
          $scope.currentSize = {
            min: serverGroup.asg.minSize,
            max: serverGroup.asg.maxSize,
            desired: serverGroup.asg.desiredCapacity
          };

          $scope.verification = {
            required: accountService.challengeDestructiveActions(serverGroup.account)
          };

          $scope.command = angular.copy($scope.currentSize);
          $scope.command.advancedMode = serverGroup.asg.minSize !== serverGroup.asg.maxSize;

          $scope.isValid = function () {
            var command = $scope.command;
            if ($scope.verification.required && $scope.verification.verifyAccount !== serverGroup.account) {
              return false;
            }
            return command.advancedMode ?
              command.min <= command.max && command.desired >= command.min && command.desired <= command.max :
              command.newSize !== null;
          };

          $scope.resize = function () {
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

          $scope.cancel = function () {
            $modalInstance.dismiss();
          };
        }
      });
    };

  }
);
