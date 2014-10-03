'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceDetailsCtrl', function ($scope, $rootScope, instance, application, orcaService, confirmationModalService) {

    function extractHealthMetrics(instance) {
      if (!instance.health) {
        $scope.healthMetrics = [];
        return;
      }
      var displayableMetrics = instance.health.filter(
        function(metric) {
          return metric.type !== 'Amazon' || metric.state !== 'Unknown';
        });
      $scope.healthMetrics = displayableMetrics;
    }

    function extractInstance() {
      console.warn('extracting instance...');
      application.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.instanceId === instance.instanceId) {
              $scope.instance = possibleInstance;
              extractHealthMetrics(possibleInstance);
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

    this.terminateInstance = function terminateInstance() {
      var instance = $scope.instance;
      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + '?',
        buttonText: 'Terminate ' + instance.instanceId,
        destructive: true,
        account: instance.account
      }).then(function () {
        orcaService.terminateInstance(instance, $scope.application.name)
          .then(function (response) {
            console.warn('task: ', response.ref);
          });
      });
    };

    extractInstance();

    application.registerAutoRefreshHandler(extractInstance, $scope);

    $scope.account = instance.account;

  }
);
