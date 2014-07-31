'use strict';

angular.module('deckApp')
  .controller('InstanceCtrl', function($scope, $rootScope, instance, application) {

    function extractInstance() {
      application.data.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.name === instance.name) {
              $scope.instance = possibleInstance;
              // bind healthStatus from asg instance
              var asgInstances = serverGroup.asg.instances.filter(function(asgInstance) {
                return asgInstance.instanceId === instance.name;
              });
              if (asgInstances.length) {
                possibleInstance.healthStatus = asgInstances[0].healthStatus;
              }
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

    if (application.data.clusters && application.data.clusters.length) {
      extractInstance();
    } else {
      $scope.$on('clustersLoaded', extractInstance);
    }

    $scope.account = instance.account;

  })
;
