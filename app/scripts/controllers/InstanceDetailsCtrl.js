'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceDetailsCtrl', function ($scope, $rootScope, instance, application, pond, confirmationModalService) {

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

    // TODO: move to service
    $scope.terminateInstance = function () {
      var instance = $scope.instance;
      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + '?',
        buttonText: 'Terminate ' + instance.instanceId,
        destructive: true,
        account: instance.account
      }).then(function () {
        pond.one('ops').customPOST([
          {
            type: 'terminateInstances',
            instanceIds: [instance.instanceId],
            region: instance.region,
            credentials: instance.account,
            user: 'chrisb'
          }
        ]).then(function (response) {
          console.warn('task: ', response.ref);
        });
      });
    };

    extractInstance(application.clusters);

    $scope.account = instance.account;

  }
);
