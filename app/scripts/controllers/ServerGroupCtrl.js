'use strict';

angular.module('deckApp')
  .controller('ServerGroupCtrl', function($scope, application, serverGroup, pond, $modal, confirmationModalService) {

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

    // TODO: move to service
    $scope.destroyServerGroup = function() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name
      }).then(function() {
        pond.one('ops').customPOST([{
          asgName: serverGroup.name,
          type: 'destroyAsg',
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          user: 'chrisb'
        }]).then(function(response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    // TODO: move to service
    $scope.disableServerGroup = function() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name
      }).then(function() {
        pond.one('ops').customPOST([{
          asgName: serverGroup.name,
          type: 'disableAsg',
          regions: [serverGroup.region],
          credentials: serverGroup.account,
          user: 'chrisb'
        }]).then(function(response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    $scope.resizeServerGroup = function() {
      var serverGroup = $scope.serverGroup;
      $modal.open({
        templateUrl: 'views/application/modal/resizeServerGroup.html',
        controller: function($scope, $modalInstance, pond) {

          $scope.serverGroup = serverGroup;
          $scope.currentSize = {
            min: serverGroup.asg.minSize,
            max: serverGroup.asg.maxSize,
            desired: serverGroup.asg.desiredCapacity
          };

          $scope.command = angular.copy($scope.currentSize);
          $scope.command.advancedMode = serverGroup.asg.minSize !== serverGroup.asg.maxSize;

          $scope.isValid = function() {
            var command = $scope.command;
            return command.advancedMode ?
              command.min <= command.max && command.desired >= command.min && command.desired <= command.max :
              command.newSize !== null;
          };

          $scope.resize = function() {
            var capacity = { min: $scope.command.min, max: $scope.command.max, desired: $scope.command.desired };
            if (!$scope.command.advancedMode) {
              capacity = { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize };
            }
            console.warn('capacity:', capacity);
            pond.one('ops').customPOST([{
              asgName: serverGroup.name,
              type: 'resizeAsg',
              regions: [serverGroup.region],
              credentials: serverGroup.account,
              user: 'deckUser',
              capacity: capacity
            }]).then(function(response) {
              $modalInstance.close();
              console.warn('task:', response.ref);
            });
          };

          $scope.cancel = function() {
            $modalInstance.dismiss();
          };
        }
      });
    };

  })
;
