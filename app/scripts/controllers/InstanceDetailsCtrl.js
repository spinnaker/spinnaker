'use strict';


angular.module('deckApp')
  .controller('InstanceDetailsCtrl', function ($scope, $state, notificationsService, instance, application,
                                               instanceWriter, oortService, confirmationModalService) {

    $scope.state = {
      loading: true
    };

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

    function retrieveInstance() {
      var instanceSummary, account, region;
      application.clusters.some(function (cluster) {
        return cluster.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
            if (possibleInstance.id === instance.instanceId) {
              instanceSummary = possibleInstance;
              account = serverGroup.account;
              region = serverGroup.region;
              return true;
            }
          });
        });
      });

      if (instanceSummary && account && region) {
        oortService.getInstanceDetails(account, region, instance.instanceId).then(function(details) {
          $scope.state.loading = false;
          $scope.instance = angular.extend(details.plain(), instanceSummary);
          extractHealthMetrics(details);
          $scope.instance.account = account;
          $scope.instance.region = region;
          $scope.baseIpAddress = details.publicDnsName || details.privateIpAddress;
        },
        function() {
          // When an instance is first starting up, we may not have the details cached in oort yet, but we still
          // want to let the user see what details we have
          $scope.state.loading = false;
        });
      }
      if (!instanceSummary) {
        notificationsService.create({
          message: 'Could not find instance "' + instance.instanceId,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
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
        return instanceWriter.terminateInstance(instance, $scope.application.name);
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

    retrieveInstance();

    application.registerAutoRefreshHandler(retrieveInstance, $scope);

    $scope.account = instance.account;

  }
);
