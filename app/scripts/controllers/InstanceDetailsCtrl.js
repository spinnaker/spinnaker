'use strict';


angular.module('deckApp')
  .controller('InstanceDetailsCtrl', function ($scope, $state, notifications, instance, application, orcaService, confirmationModalService) {

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
      application.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.instanceId === instance.instanceId) {
              $scope.instance = possibleInstance;
              extractHealthMetrics(possibleInstance);
              $scope.baseIpAddress = possibleInstance.publicDnsName || possibleInstance.privateIpAddress;
              return true;
            }
          });
        });
      });
      if (!$scope.instance) {
        notifications.create({
          message: 'Could not find instance "' + instance.instanceId,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
        instance.notFound = true;
        instance.healthStatus = 'Unhealthy';
        $scope.instance = instance;
      }
    }

    this.terminateInstance = function terminateInstance() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: application,
        title: 'Terminating ' + instance.instanceId,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        onApplicationRefresh: function() {
          if ($state.includes('**.instanceDetails', {instanceId: instance.instanceId})) {
            $state.go('^');
          }
        }
      };

      var submitMethod = function () {
        return orcaService.terminateInstance(instance, $scope.application.name);
      };

      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + '?',
        buttonText: 'Terminate ' + instance.instanceId,
        destructive: false,
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    extractInstance();

    application.registerAutoRefreshHandler(extractInstance, $scope);

    $scope.account = instance.account;

  }
);
