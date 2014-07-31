'use strict';

angular.module('deckApp')
  .controller('InstanceCtrl', function($scope, $rootScope, instance, application) {

    if (application.data.clusters && application.data.clusters.length) {
      extractInstance();
    } else {
      $scope.$on('clustersLoaded', extractInstance);
    }

    function extractInstance() {
      application.data.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.name === instance.name) {
              $scope.instance = possibleInstance;
              return true;
            }
          });
        });
      });
    }

    $scope.account = instance.account;

  })
;
