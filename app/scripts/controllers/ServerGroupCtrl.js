'use strict';

angular.module('deckApp')
  .controller('ServerGroupCtrl', function($scope, application, serverGroup, pond, $modal, confirmationModalService) {

    function extractServerGroup() {
      application.data.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (toCheck) {
          if (toCheck.name === serverGroup.name && toCheck.account === serverGroup.accountId && toCheck.region === serverGroup.region) {
            $scope.serverGroup = toCheck;
            $scope.cluster = cluster;
            $scope.account = serverGroup.accountId;
            delete $scope.serverGroup.launchConfig.userData;
            return true;
          }
        });
      });
    }

    if (application.data.clusters && application.data.clusters.length) {
      extractServerGroup();
    } else {
      $scope.$on('clustersLoaded', extractServerGroup);
    }


    // TODO: move to service
    $scope.destroyServerGroup = function() {
      var serverGroup = $scope.serverGroup;
      confirmationModalService.confirm({
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        size: 'sm'
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
        buttonText: 'Disable ' + serverGroup.name,
        size: 'sm'
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
        size: 'sm',
        controller: function($scope, $modalInstance, pond) {

          $scope.serverGroup = serverGroup;
          console.warn('group:', serverGroup);
          $scope.currentSize = serverGroup.instances.length;

          $scope.command = {
            newSize: serverGroup.instances.length
          };

          $scope.resize = function() {
            pond.one('ops').customPOST([{
              asgName: serverGroup.name,
              type: 'resizeAsg',
              regions: [serverGroup.region],
              credentials: serverGroup.account,
              user: 'chrisb',
              capacity: { min: $scope.command.newSize, max: $scope.command.newSize, desired: $scope.command.newSize }
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
