'use strict';

angular.module('deckApp')
  .controller('InstanceCtrl', function($scope, $rootScope, instance, application) {

    function extractInstance(clusters) {
      clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.instanceId === instance.instanceId) {
              $scope.instance = possibleInstance;
              return true;
            }
          });
        });
      });
      if (!$scope.instance) {
        instance.notFound = true;
        instance.healthStatus = 'Unhealthy';
        $scope.instance = instance;
      }
    }

    application.getClusters().then(extractInstance.bind(null));

    $scope.account = instance.account;

  })
;
